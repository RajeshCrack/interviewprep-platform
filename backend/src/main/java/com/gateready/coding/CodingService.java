package com.gateready.coding;

import com.gateready.coding.CodeRunner.Outcome;
import com.gateready.coding.CodingDtos.*;
import com.gateready.common.ApiException;
import com.gateready.course.CourseDtos.RoadmapTopic;
import com.gateready.course.CourseDtos.TopicStatus;
import com.gateready.course.CourseService;
import com.gateready.engage.BadgeService;
import com.gateready.gate.GateRules;
import com.gateready.user.UserStats;
import com.gateready.user.UserStatsRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The coding round. The round opens only after the MCQ test is passed, every query filters by the user
 * id from the token, and hidden test data never leaves the server. Judging runs outside a database
 * transaction so a slow run does not hold a connection.
 */
@Service
public class CodingService {
    static final int MAX_CODE_CHARS = 20_000;
    static final int MAX_SUBMISSIONS_PER_MINUTE = 10;
    static final int PASS_XP = 200;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CourseService courses;
    private final UserStatsRepository stats;
    private final CodeRunner runner;
    private final Clock clock;
    private final BadgeService badges;

    public CodingService(JdbcTemplate jdbc, TransactionTemplate tx, CourseService courses, UserStatsRepository stats,
                         CodeRunner runner, Clock clock, BadgeService badges) {
        this.badges = badges;
        this.jdbc = jdbc;
        this.tx = tx;
        this.courses = courses;
        this.stats = stats;
        this.runner = runner;
        this.clock = clock;
    }

    private record Problem(UUID id, UUID topicId, int position, String difficulty, String title, String statement, String starter) {}

    public RoundView round(UUID userId, UUID topicId) {
        requireRoundOpen(userId, topicId);
        List<Problem> problems = problems(topicId);
        List<UUID> solved = jdbc.query("SELECT problem_id FROM coding_solved WHERE user_id = ?", (rs, i) -> rs.getObject(1, UUID.class), userId);
        List<ProblemView> views = new ArrayList<>();
        for (Problem p : problems) {
            List<Sample> samples = jdbc.query("SELECT input, expected FROM coding_tests WHERE problem_id = ? AND visible ORDER BY position",
                    (rs, i) -> new Sample(rs.getString(1), rs.getString(2)), p.id());
            String last = jdbc.query("SELECT code FROM coding_submissions WHERE user_id = ? AND problem_id = ? ORDER BY created_at DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, userId, p.id());
            views.add(new ProblemView(p.id(), p.position(), p.difficulty(), p.title(), p.statement(), p.starter(), samples,
                    solved.contains(p.id()), last));
        }
        int solvedCount = (int) problems.stream().filter(p -> solved.contains(p.id())).count();
        int solvedEasy = (int) problems.stream().filter(p -> p.difficulty().equals("EASY") && solved.contains(p.id())).count();
        int totalEasy = (int) problems.stream().filter(p -> p.difficulty().equals("EASY")).count();
        boolean passed = Boolean.TRUE.equals(jdbc.query("SELECT coding_passed_at IS NOT NULL FROM topic_progress WHERE user_id = ? AND topic_id = ?",
                rs -> rs.next() && rs.getBoolean(1), userId, topicId));
        return new RoundView(topicId, passed, solvedCount, problems.size(), solvedEasy, totalEasy, GateRules.CODING_MIN_SOLVED, views);
    }

    /** RUN checks the visible samples only. SUBMIT checks every test and can solve the problem. */
    public SubmissionResult submit(UUID userId, UUID problemId, String code, String modeText) {
        String mode = "RUN".equalsIgnoreCase(modeText) ? "RUN" : "SUBMIT".equalsIgnoreCase(modeText) ? "SUBMIT" : null;
        if (mode == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Mode must be RUN or SUBMIT.");
        if (code == null || code.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Write some code first.");
        if (code.length() > MAX_CODE_CHARS) throw new ApiException(HttpStatus.BAD_REQUEST, "Code is too long (limit " + MAX_CODE_CHARS + " characters).");

        Problem problem = problem(problemId);
        requireRoundOpen(userId, problem.topicId());

        Instant now = clock.instant();
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM coding_submissions WHERE user_id = ? AND created_at > ?",
                Integer.class, userId, Timestamp.from(now.minusSeconds(60)));
        if (recent != null && recent >= MAX_SUBMISSIONS_PER_MINUTE) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Slow down: at most " + MAX_SUBMISSIONS_PER_MINUTE + " runs a minute.");
        }

        record T(int position, String input, String expected, boolean visible) {}
        List<T> tests = jdbc.query("SELECT position, input, expected, visible FROM coding_tests WHERE problem_id = ?"
                        + (mode.equals("RUN") ? " AND visible" : "") + " ORDER BY position",
                (rs, i) -> new T(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), problemId);

        List<TestResult> results = new ArrayList<>();
        String overall = "ACCEPTED";
        String message = null;
        int passed = 0;
        for (int i = 0; i < tests.size(); i++) {
            T t = tests.get(i);
            Outcome o = runner.run(code, t.input());
            if (o.status() == CodeRunner.Status.UNAVAILABLE) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Code judging is not available right now. Try again in a minute.");
            }
            if (o.status() == CodeRunner.Status.COMPILE_ERROR) {
                overall = "COMPILE_ERROR";
                message = truncate(o.stderr(), 4000);
                for (T rest : tests) results.add(new TestResult(rest.position(), rest.visible(), false, "NOT_RUN", null, null, null, null));
                break;
            }
            boolean ok = o.status() == CodeRunner.Status.OK && OutputComparer.matches(t.expected(), o.stdout());
            String status = ok ? "PASSED" : switch (o.status()) {
                case TIME_LIMIT -> "TIME_LIMIT";
                case RUNTIME_ERROR -> "RUNTIME_ERROR";
                default -> "WRONG_ANSWER";
            };
            if (ok) passed++;
            else if (overall.equals("ACCEPTED")) overall = status;
            results.add(new TestResult(t.position(), t.visible(), ok, status,
                    t.visible() ? t.input() : null, t.visible() ? t.expected() : null,
                    t.visible() ? truncate(o.stdout(), 2000) : null,
                    t.visible() && !o.stderr().isBlank() ? truncate(o.stderr(), 2000) : null));
            if (o.status() == CodeRunner.Status.TIME_LIMIT) { // do not wait for more timeouts
                for (T rest : tests.subList(i + 1, tests.size())) results.add(new TestResult(rest.position(), rest.visible(), false, "NOT_RUN", null, null, null, null));
                break;
            }
        }
        boolean allPassed = overall.equals("ACCEPTED") && passed == tests.size();
        int total = tests.size();
        final String finalStatus = overall;
        final int finalPassed = passed;
        final String finalMessage = message;

        return tx.execute(s -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO coding_submissions (id, user_id, problem_id, mode, code, status, passed_tests, total_tests, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, userId, problemId, mode, code, finalStatus, finalPassed, total, Timestamp.from(now));
            boolean solvedNow = false;
            int xp = 0;
            if (mode.equals("SUBMIT") && allPassed) {
                jdbc.update("INSERT INTO coding_solved (user_id, problem_id, solved_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        userId, problemId, Timestamp.from(now));
                solvedNow = true;
                int solvedEasy = count(userId, problem.topicId(), true);
                int solvedTotal = count(userId, problem.topicId(), false);
                if (GateRules.codingPassed(solvedEasy, solvedTotal)) {
                    int updated = jdbc.update("UPDATE topic_progress SET coding_passed_at = ? WHERE user_id = ? AND topic_id = ? AND coding_passed_at IS NULL",
                            Timestamp.from(now), userId, problem.topicId());
                    if (updated == 1) { // only the first pass pays out, even if two submits race
                        UserStats st = stats.findById(userId).orElseGet(() -> new UserStats(userId));
                        st.addXp(PASS_XP);
                        stats.save(st);
                        xp = PASS_XP;
                    }
                }
            }
            if (solvedNow) badges.award(userId);
            if (!solvedNow) {
                solvedNow = Boolean.TRUE.equals(jdbc.query("SELECT true FROM coding_solved WHERE user_id = ? AND problem_id = ?",
                        rs -> rs.next() ? Boolean.TRUE : Boolean.FALSE, userId, problemId));
            }
            boolean roundPassed = Boolean.TRUE.equals(jdbc.query("SELECT coding_passed_at IS NOT NULL FROM topic_progress WHERE user_id = ? AND topic_id = ?",
                    rs -> rs.next() && rs.getBoolean(1), userId, problem.topicId()));
            return new SubmissionResult(id, mode, finalStatus, finalPassed, total, finalMessage, results, solvedNow, roundPassed, xp);
        });
    }

    // ---- helpers -------------------------------------------------------------------------

    private void requireRoundOpen(UUID userId, UUID topicId) {
        RoadmapTopic entry = courses.entryFor(userId, topicId);
        if (entry.status() == TopicStatus.LOCKED || !GateRules.codingRoundOpen(entry.mcqPassed())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Pass the MCQ test for this topic to open the coding round.");
        }
    }

    private int count(UUID userId, UUID topicId, boolean easyOnly) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM coding_solved s JOIN coding_problems p ON p.id = s.problem_id
                WHERE s.user_id = ? AND p.topic_id = ?""" + (easyOnly ? " AND p.difficulty = 'EASY'" : ""),
                Integer.class, userId, topicId);
        return n == null ? 0 : n;
    }

    private List<Problem> problems(UUID topicId) {
        return jdbc.query("SELECT id, topic_id, position, difficulty, title, statement, starter_code FROM coding_problems WHERE topic_id = ? ORDER BY position",
                (rs, i) -> new Problem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)), topicId);
    }

    private Problem problem(UUID problemId) {
        List<Problem> found = jdbc.query("SELECT id, topic_id, position, difficulty, title, statement, starter_code FROM coding_problems WHERE id = ?",
                (rs, i) -> new Problem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)), problemId);
        if (found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Problem not found.");
        return found.get(0);
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "\n… (cut)";
    }
}
