package com.gateready.mcq;

import com.gateready.common.ApiException;
import com.gateready.course.CourseDtos.RoadmapTopic;
import com.gateready.course.CourseDtos.TopicStatus;
import com.gateready.course.CourseService;
import com.gateready.gate.GateRules;
import com.gateready.gate.GateRules.Difficulty;
import com.gateready.gate.GateRules.Kind;
import com.gateready.mcq.McqDrawer.Candidate;
import com.gateready.mcq.McqDtos.*;
import com.gateready.user.UserStats;
import com.gateready.user.UserStatsRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The MCQ gate. Every query filters by the user id from the token, and nothing the client sends
 * decides a score: answers are graded here against a key the client never receives.
 */
@Service
public class McqService {
    static final Duration TIME_LIMIT = Duration.ofMinutes(30);
    /** A submit that arrives a little late (slow network) still counts. */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(60);
    static final int PASS_XP = 100;

    private final JdbcTemplate jdbc;
    private final CourseService courses;
    private final UserStatsRepository stats;
    private final Clock clock;

    public McqService(JdbcTemplate jdbc, CourseService courses, UserStatsRepository stats, Clock clock) {
        this.jdbc = jdbc;
        this.courses = courses;
        this.stats = stats;
        this.clock = clock;
    }

    /** Starts a test, or returns the one already running so a refresh never costs the student an attempt. */
    @Transactional
    public AttemptView start(UUID userId, UUID topicId) {
        RoadmapTopic entry = courses.entryFor(userId, topicId);
        if (entry.status() == TopicStatus.LOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Pass the previous topic to unlock this one.");
        }
        if (entry.mcqPassed()) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already passed the test for this topic.");
        }
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, userId + ":" + topicId);

        Instant now = clock.instant();
        jdbc.update("UPDATE mcq_attempts SET status = 'EXPIRED' WHERE user_id = ? AND topic_id = ? AND status = 'ACTIVE' AND expires_at <= ?",
                userId, topicId, Timestamp.from(now));
        List<UUID> running = jdbc.query("SELECT id FROM mcq_attempts WHERE user_id = ? AND topic_id = ? AND status = 'ACTIVE'",
                (rs, i) -> rs.getObject(1, UUID.class), userId, topicId);
        if (!running.isEmpty()) return view(userId, running.get(0));

        List<Candidate> pool = jdbc.query("SELECT id, kind, difficulty FROM mcq_questions WHERE topic_id = ?",
                (rs, i) -> new Candidate(rs.getObject("id", UUID.class), Kind.valueOf(rs.getString("kind")),
                        Difficulty.valueOf(rs.getString("difficulty"))), topicId);
        Set<UUID> seen = new HashSet<>(jdbc.query("""
                SELECT aq.question_id FROM mcq_attempt_questions aq
                WHERE aq.attempt_id = (SELECT id FROM mcq_attempts WHERE user_id = ? AND topic_id = ?
                                       ORDER BY started_at DESC LIMIT 1)""",
                (rs, i) -> rs.getObject(1, UUID.class), userId, topicId));

        var random = ThreadLocalRandom.current();
        List<Candidate> drawn;
        try {
            drawn = McqDrawer.draw(pool, seen, random);
        } catch (IllegalStateException tooFewQuestions) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The test for this topic is not ready yet.");
        }
        if (!GateRules.validMcqComposition(drawn.stream().map(c -> new GateRules.Question(c.kind(), c.difficulty())).toList())) {
            throw new IllegalStateException("Drawn test has the wrong composition");
        }

        UUID attemptId = UUID.randomUUID();
        jdbc.update("INSERT INTO mcq_attempts (id, user_id, topic_id, status, started_at, expires_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                attemptId, userId, topicId, Timestamp.from(now), Timestamp.from(now.plus(TIME_LIMIT)));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < drawn.size(); i++) {
            rows.add(new Object[]{attemptId, i + 1, drawn.get(i).id(), McqDrawer.shuffledOrder(random)});
        }
        jdbc.batchUpdate("INSERT INTO mcq_attempt_questions (attempt_id, position, question_id, option_order) VALUES (?, ?, ?, ?)", rows);
        return view(userId, attemptId);
    }

    /** The running test, for resuming after a refresh. 409 once submitted, 410 once the time is up. */
    @Transactional(noRollbackFor = ApiException.class)
    public AttemptView view(UUID userId, UUID attemptId) {
        Attempt a = attempt(userId, attemptId, false);
        requireRunning(a, Duration.ZERO);
        List<QuestionView> questions = jdbc.query(QUESTIONS_SQL, (rs, i) -> {
            String order = rs.getString("option_order");
            return new QuestionView(rs.getInt("position"), Kind.valueOf(rs.getString("kind")), rs.getString("prompt"),
                    rs.getString("code_snippet"), shownOptions(rs, order), selected(rs));
        }, attemptId);
        return new AttemptView(a.id, a.topicId, a.startedAt, a.expiresAt, questions.size(), questions);
    }

    /** Saves one answer so progress survives a refresh. selected null clears it. */
    @Transactional(noRollbackFor = ApiException.class)
    public void saveAnswer(UUID userId, UUID attemptId, int position, Integer selected) {
        checkChoice(selected);
        Attempt a = attempt(userId, attemptId, false);
        requireRunning(a, Duration.ZERO);
        int n = jdbc.update("UPDATE mcq_attempt_questions SET selected_display = ? WHERE attempt_id = ? AND position = ?",
                selected, attemptId, position);
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Question not found.");
    }

    /** Grades the test. answers (optional) holds 20 entries, each 0 to 3 or null for blank. */
    @Transactional(noRollbackFor = ApiException.class)
    public ResultView submit(UUID userId, UUID attemptId, List<Integer> answers) {
        Attempt a = attempt(userId, attemptId, true);
        requireRunning(a, SUBMIT_GRACE);
        if (answers != null) {
            if (answers.size() != GateRules.MCQ_QUESTIONS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Send one answer for each of the " + GateRules.MCQ_QUESTIONS + " questions.");
            }
            answers.forEach(McqService::checkChoice);
            for (int i = 0; i < answers.size(); i++) {
                jdbc.update("UPDATE mcq_attempt_questions SET selected_display = ? WHERE attempt_id = ? AND position = ?",
                        answers.get(i), attemptId, i + 1);
            }
        }
        List<Row> rows = rows(attemptId);
        McqGrader.Result graded = McqGrader.grade(rows.stream().map(Row::answer).toList());

        Instant now = clock.instant();
        jdbc.update("UPDATE mcq_attempts SET status = 'SUBMITTED', submitted_at = ?, score = ?, passed = ? WHERE id = ?",
                Timestamp.from(now), graded.correct(), graded.passed(), attemptId);

        int xp = 0;
        if (graded.passed()) {
            boolean first = Boolean.TRUE.equals(jdbc.query(
                    "SELECT mcq_passed_at IS NULL FROM topic_progress WHERE user_id = ? AND topic_id = ?",
                    rs -> rs.next() ? rs.getBoolean(1) : true, userId, a.topicId));
            jdbc.update("""
                    INSERT INTO topic_progress (user_id, topic_id, mcq_passed_at) VALUES (?, ?, ?)
                    ON CONFLICT (user_id, topic_id)
                    DO UPDATE SET mcq_passed_at = COALESCE(topic_progress.mcq_passed_at, EXCLUDED.mcq_passed_at)""",
                    userId, a.topicId, Timestamp.from(now));
            if (first) {
                UserStats s = stats.findById(userId).orElseGet(() -> new UserStats(userId));
                s.addXp(PASS_XP);
                stats.save(s);
                xp = PASS_XP;
            }
        }
        return resultView(a, rows, graded, now, xp);
    }

    @Transactional(readOnly = true)
    public ResultView result(UUID userId, UUID attemptId) {
        Attempt a = attempt(userId, attemptId, false);
        if (!"SUBMITTED".equals(a.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "This test has not been submitted.");
        }
        List<Row> rows = rows(attemptId);
        int xp = 0; // the award is reported once, in the submit response
        return resultView(a, rows, McqGrader.grade(rows.stream().map(Row::answer).toList()), a.submittedAt, xp);
    }

    // ---- helpers -------------------------------------------------------------------------

    private static final String QUESTIONS_SQL = """
            SELECT aq.position, aq.option_order, aq.selected_display, q.kind, q.difficulty, q.prompt, q.code_snippet,
                   q.option_1, q.option_2, q.option_3, q.option_4, q.correct_option, q.explanation
            FROM mcq_attempt_questions aq JOIN mcq_questions q ON q.id = aq.question_id
            WHERE aq.attempt_id = ? ORDER BY aq.position""";

    private record Attempt(UUID id, UUID topicId, String status, Instant startedAt, Instant expiresAt, Instant submittedAt) {}

    private record Row(int position, Kind kind, Difficulty difficulty, String prompt, String code, List<String> shown,
                       Integer selected, String explanation, McqGrader.Answer answer) {}

    private Attempt attempt(UUID userId, UUID attemptId, boolean lock) {
        List<Attempt> found = jdbc.query("SELECT id, topic_id, status, started_at, expires_at, submitted_at FROM mcq_attempts WHERE id = ? AND user_id = ?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, i) -> new Attempt(rs.getObject("id", UUID.class), rs.getObject("topic_id", UUID.class), rs.getString("status"),
                        rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant()),
                attemptId, userId);
        if (found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Test not found.");
        return found.get(0);
    }

    private void requireRunning(Attempt a, Duration grace) {
        if ("SUBMITTED".equals(a.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "This test was already submitted.");
        }
        if ("EXPIRED".equals(a.status) || clock.instant().isAfter(a.expiresAt.plus(grace))) {
            jdbc.update("UPDATE mcq_attempts SET status = 'EXPIRED' WHERE id = ? AND status = 'ACTIVE'", a.id);
            throw new ApiException(HttpStatus.GONE, "Time is up for this test. Start a new one.");
        }
    }

    private static void checkChoice(Integer selected) {
        if (selected != null && (selected < 0 || selected > 3)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose option 1 to 4.");
        }
    }

    private List<Row> rows(UUID attemptId) {
        return jdbc.query(QUESTIONS_SQL, (rs, i) -> {
            String order = rs.getString("option_order");
            Kind kind = Kind.valueOf(rs.getString("kind"));
            Difficulty difficulty = Difficulty.valueOf(rs.getString("difficulty"));
            Integer selected = selected(rs);
            return new Row(rs.getInt("position"), kind, difficulty, rs.getString("prompt"), rs.getString("code_snippet"),
                    shownOptions(rs, order), selected, rs.getString("explanation"),
                    new McqGrader.Answer(kind, difficulty, rs.getInt("correct_option"), order, selected));
        }, attemptId);
    }

    private static Integer selected(java.sql.ResultSet rs) throws java.sql.SQLException {
        int v = rs.getInt("selected_display");
        return rs.wasNull() ? null : v;
    }

    private static List<String> shownOptions(java.sql.ResultSet rs, String order) throws java.sql.SQLException {
        String[] stored = {rs.getString("option_1"), rs.getString("option_2"), rs.getString("option_3"), rs.getString("option_4")};
        List<String> shown = new ArrayList<>();
        for (int k = 0; k < 4; k++) shown.add(stored[order.charAt(k) - '0']);
        return shown;
    }

    private ResultView resultView(Attempt a, List<Row> rows, McqGrader.Result graded, Instant submittedAt, int xp) {
        List<GroupView> groups = graded.groups().stream()
                .map(g -> new GroupView(g.kind(), g.difficulty(), g.correct(), g.total())).toList();
        List<ReviewItem> review = rows.stream().map(r -> new ReviewItem(r.position(), r.kind(), r.difficulty(), r.prompt(),
                r.code(), r.shown(), r.selected(), r.answer().correctDisplay(), r.answer().correct(), r.explanation())).toList();
        return new ResultView(a.id, a.topicId, graded.correct(), GateRules.MCQ_QUESTIONS, GateRules.passMark(), graded.passed(),
                submittedAt, xp, groups, review);
    }
}
