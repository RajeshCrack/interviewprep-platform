package com.gateready.course;

import com.gateready.common.ApiException;
import com.gateready.course.CourseDtos.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every query that touches progress filters by the user id from the token. */
@Service
public class CourseService {
    private final JdbcTemplate jdbc;

    public CourseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<CourseSummary> courses() {
        return jdbc.query("""
                SELECT c.slug, c.title, c.description, c.icon, c.published,
                       (SELECT count(*) FROM topics t WHERE t.course_id = c.id) AS topic_count
                FROM courses c ORDER BY c.position""",
                (rs, i) -> new CourseSummary(rs.getString("slug"), rs.getString("title"), rs.getString("description"),
                        rs.getString("icon"), rs.getBoolean("published"), rs.getInt("topic_count")));
    }

    @Transactional(readOnly = true)
    public Roadmap roadmap(UUID userId, String slug) {
        CourseSummary course = courses().stream().filter(c -> c.slug().equals(slug)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found."));
        if (!course.published()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This course is not available yet.");
        }
        List<TopicRow> rows = jdbc.query("""
                SELECT t.id, t.position, t.title, t.est_minutes, t.has_content,
                       (p.mcq_passed_at IS NOT NULL) AS mcq, (p.coding_passed_at IS NOT NULL) AS coding
                FROM topics t
                JOIN courses c ON c.id = t.course_id
                LEFT JOIN topic_progress p ON p.topic_id = t.id AND p.user_id = ?
                WHERE c.slug = ?
                ORDER BY t.position""",
                (rs, i) -> new TopicRow(rs.getObject("id", UUID.class), rs.getInt("position"), rs.getString("title"),
                        rs.getInt("est_minutes"), rs.getBoolean("has_content"), rs.getBoolean("mcq"), rs.getBoolean("coding")),
                userId, slug);
        List<RoadmapTopic> topics = RoadmapCalculator.compute(rows);
        int passed = (int) topics.stream().filter(t -> t.status() == TopicStatus.PASSED).count();
        return new Roadmap(course, passed, topics);
    }

    /** The topic's place on this student's roadmap: PASSED, CURRENT or LOCKED. 404 when the topic does not exist. */
    @Transactional(readOnly = true)
    public RoadmapTopic entryFor(UUID userId, UUID topicId) {
        String slug = jdbc.query("SELECT c.slug FROM topics t JOIN courses c ON c.id = t.course_id WHERE t.id = ?",
                rs -> rs.next() ? rs.getString(1) : null, topicId);
        if (slug == null) throw new ApiException(HttpStatus.NOT_FOUND, "Topic not found.");
        return roadmap(userId, slug).topics().stream().filter(t -> t.id().equals(topicId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public TopicDetail topic(UUID userId, UUID topicId) {
        String slug = jdbc.query("SELECT c.slug FROM topics t JOIN courses c ON c.id = t.course_id WHERE t.id = ?",
                rs -> rs.next() ? rs.getString(1) : null, topicId);
        if (slug == null) throw new ApiException(HttpStatus.NOT_FOUND, "Topic not found.");

        Roadmap roadmap = roadmap(userId, slug);
        RoadmapTopic entry = roadmap.topics().stream().filter(t -> t.id().equals(topicId)).findFirst().orElseThrow();
        if (entry.status() == TopicStatus.LOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Pass the previous topic to unlock this one.");
        }
        UUID next = roadmap.topics().stream().filter(t -> t.position() > entry.position()).findFirst()
                .map(RoadmapTopic::id).orElse(null);
        return jdbc.query("""
                SELECT what_text, why_text, example_code, hints, video_query FROM topics WHERE id = ?""",
                rs -> {
                    rs.next();
                    String hints = rs.getString("hints");
                    List<String> hintList = hints == null || hints.isBlank() ? List.of()
                            : Arrays.stream(hints.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                    return new TopicDetail(entry.id(), slug, entry.position(), roadmap.topics().size(), entry.title(),
                            entry.estMinutes(), entry.hasContent(), rs.getString("what_text"), rs.getString("why_text"),
                            rs.getString("example_code"), hintList, rs.getString("video_query"),
                            entry.mcqPassed(), entry.codingPassed(), next);
                }, topicId);
    }
}
