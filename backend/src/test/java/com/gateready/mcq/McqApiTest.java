package com.gateready.mcq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McqApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE users CASCADE"); // attempts and progress follow; questions are seed data
    }

    private record Student(String token, UUID id) {}

    private Student register(String email) throws Exception {
        String res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Test Student", "email", email, "password", "password1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(res);
        return new Student(n.get("accessToken").asText(), UUID.fromString(n.at("/user/id").asText()));
    }

    private UUID topicId(int position) {
        return jdbc.queryForObject("SELECT t.id FROM topics t JOIN courses c ON c.id = t.course_id WHERE c.slug = 'java' AND t.position = ?",
                UUID.class, position);
    }

    private ResultActions startRaw(Student s, UUID topic) throws Exception {
        return mvc.perform(post("/topics/" + topic + "/mcq/attempts").header("Authorization", "Bearer " + s.token()));
    }

    private JsonNode start(Student s) throws Exception {
        return json.readTree(startRaw(s, topicId(1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions submitRaw(Student s, String attemptId, List<Integer> answers) throws Exception {
        var req = post("/mcq/attempts/" + attemptId + "/submit").header("Authorization", "Bearer " + s.token());
        if (answers != null) req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("answers", answers)));
        return mvc.perform(req);
    }

    private JsonNode submit(Student s, String attemptId, List<Integer> answers) throws Exception {
        return json.readTree(submitRaw(s, attemptId, answers).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** The shown position of the right answer for each question, read straight from the database. */
    private List<Integer> key(String attemptId) {
        return jdbc.query("""
                SELECT aq.option_order, q.correct_option FROM mcq_attempt_questions aq
                JOIN mcq_questions q ON q.id = aq.question_id WHERE aq.attempt_id = ?::uuid ORDER BY aq.position""",
                (rs, i) -> rs.getString(1).indexOf((char) ('0' + rs.getInt(2))), attemptId);
    }

    /** An answer sheet with exactly `right` correct answers (the first ones) and the rest wrong. */
    private List<Integer> sheet(String attemptId, int right) {
        List<Integer> key = key(attemptId);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < key.size(); i++) out.add(i < right ? key.get(i) : (key.get(i) + 1) % 4);
        return out;
    }

    /** Several questions share a prompt such as "What does this program print?", so identify by prompt, code and options. */
    private static String identity(JsonNode q) {
        return q.get("prompt").asText() + "|" + q.path("codeSnippet").asText() + "|" + q.get("options").toString();
    }

    private int xp(Student s) {
        Integer v = jdbc.query("SELECT xp FROM user_stats WHERE user_id = ?", rs -> rs.next() ? rs.getInt(1) : 0, s.id());
        return v == null ? 0 : v;
    }

    @Test
    void needsALogin() throws Exception {
        mvc.perform(post("/topics/" + topicId(1) + "/mcq/attempts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/mcq/attempts/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void startDrawsTwentyQuestionsAndNeverLeaksTheKey() throws Exception {
        Student s = register("a@college.edu");
        String raw = startRaw(s, topicId(1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode a = json.readTree(raw);
        assertThat(a.get("questions")).hasSize(20);
        assertThat(a.get("total").asInt()).isEqualTo(20);
        for (JsonNode q : a.get("questions")) {
            assertThat(q.get("options")).hasSize(4);
            assertThat(q.fieldNames()).toIterable().doesNotContain("correct", "correctOption", "explanation", "difficulty");
        }
        assertThat(raw).doesNotContain("explanation").doesNotContain("correctOption");
        long theory = a.get("questions").findValues("kind").stream().filter(k -> k.asText().equals("THEORY")).count();
        assertThat(theory).isEqualTo(10);

        // 5 of each kind and difficulty, checked in the database
        List<Integer> cells = jdbc.query("""
                SELECT count(*) FROM mcq_attempt_questions aq JOIN mcq_questions q ON q.id = aq.question_id
                WHERE aq.attempt_id = ?::uuid GROUP BY q.kind, q.difficulty""", (rs, i) -> rs.getInt(1), a.get("id").asText());
        assertThat(cells).containsExactly(5, 5, 5, 5);
        assertThat(Instant.parse(a.get("expiresAt").asText())).isAfter(Instant.parse(a.get("startedAt").asText()));
    }

    @Test
    void aLockedTopicCannotBeTested() throws Exception {
        Student s = register("a@college.edu");
        startRaw(s, topicId(2)).andExpect(status().isForbidden());
        startRaw(s, UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcq_attempts", Integer.class)).isZero();
    }

    @Test
    void startingAgainResumesTheRunningTestWithSavedAnswers() throws Exception {
        Student s = register("a@college.edu");
        JsonNode first = start(s);
        String id = first.get("id").asText();
        mvc.perform(put("/mcq/attempts/" + id + "/answers/3").header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"selected\":2}")).andExpect(status().isNoContent());

        JsonNode again = start(s);
        assertThat(again.get("id").asText()).isEqualTo(id);
        assertThat(again.get("questions")).hasSize(20);
        assertThat(again.get("questions").get(5).get("prompt")).isEqualTo(first.get("questions").get(5).get("prompt"));
        assertThat(again.get("questions").get(2).get("selected").asInt()).isEqualTo(2);
        assertThat(again.get("questions").get(0).get("selected").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcq_attempts", Integer.class)).isEqualTo(1);

        JsonNode got = json.readTree(mvc.perform(get("/mcq/attempts/" + id).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(got.get("questions").get(2).get("selected").asInt()).isEqualTo(2);
    }

    @Test
    void thirteenCorrectFailsAndTheNextTopicStaysLocked() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        JsonNode r = submit(s, id, sheet(id, 13));
        assertThat(r.get("score").asInt()).isEqualTo(13);
        assertThat(r.get("passMark").asInt()).isEqualTo(14);
        assertThat(r.get("passed").asBoolean()).isFalse();
        assertThat(r.get("xpAwarded").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM topic_progress", Integer.class)).isZero();
        assertThat(xp(s)).isZero();
    }

    @Test
    void fourteenCorrectPassesAwardsXpOnceAndStillNeedsTheCodingRound() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        JsonNode r = submit(s, id, sheet(id, 14));
        assertThat(r.get("score").asInt()).isEqualTo(14);
        assertThat(r.get("passed").asBoolean()).isTrue();
        assertThat(r.get("xpAwarded").asInt()).isEqualTo(100);
        assertThat(xp(s)).isEqualTo(100);

        // the MCQ half is recorded, the coding half is not
        assertThat(jdbc.queryForObject("SELECT mcq_passed_at IS NOT NULL AND coding_passed_at IS NULL FROM topic_progress WHERE user_id = ?",
                Boolean.class, s.id())).isTrue();
        JsonNode roadmap = json.readTree(mvc.perform(get("/courses/java/roadmap").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(roadmap.at("/topics/0/status").asText()).isEqualTo("CURRENT");
        assertThat(roadmap.at("/topics/0/mcqPassed").asBoolean()).isTrue();
        assertThat(roadmap.at("/topics/1/status").asText()).isEqualTo("LOCKED");
        startRaw(s, topicId(2)).andExpect(status().isForbidden());

        // passed already: no second test, so no second award
        startRaw(s, topicId(1)).andExpect(status().isConflict());
        assertThat(xp(s)).isEqualTo(100);
    }

    @Test
    void resultListsEveryQuestionWithTheRightAnswerAndAnExplanation() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        List<Integer> key = key(id);
        JsonNode r = submit(s, id, sheet(id, 16));
        JsonNode viaGet = json.readTree(mvc.perform(get("/mcq/attempts/" + id + "/result").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(viaGet.get("score").asInt()).isEqualTo(16);
        assertThat(viaGet.get("review")).hasSize(20);
        for (int i = 0; i < 20; i++) {
            JsonNode item = viaGet.get("review").get(i);
            assertThat(item.get("correct").asInt()).isEqualTo(key.get(i));
            assertThat(item.get("isCorrect").asBoolean()).isEqualTo(i < 16);
            assertThat(item.get("explanation").asText()).isNotBlank();
        }
        int sum = 0;
        for (JsonNode g : r.get("groups")) { assertThat(g.get("total").asInt()).isEqualTo(5); sum += g.get("correct").asInt(); }
        assertThat(sum).isEqualTo(16);
    }

    @Test
    void aRetryAfterAFailDrawsNewQuestions() throws Exception {
        Student s = register("a@college.edu");
        JsonNode first = start(s);
        Set<String> firstPrompts = new HashSet<>();
        first.get("questions").forEach(q -> firstPrompts.add(identity(q)));
        submit(s, first.get("id").asText(), sheet(first.get("id").asText(), 5));

        JsonNode second = start(s);
        assertThat(second.get("id").asText()).isNotEqualTo(first.get("id").asText());
        assertThat(second.get("questions")).hasSize(20);
        second.get("questions").forEach(q -> assertThat(firstPrompts).doesNotContain(identity(q))); // bank has 10 per group
    }

    @Test
    void submittingTwiceIsRefused() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        submit(s, id, sheet(id, 14));
        submitRaw(s, id, sheet(id, 20)).andExpect(status().isConflict());
        assertThat(xp(s)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT score FROM mcq_attempts WHERE id = ?::uuid", Integer.class, id)).isEqualTo(14);
        mvc.perform(put("/mcq/attempts/" + id + "/answers/1").header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"selected\":0}")).andExpect(status().isConflict());
    }

    @Test
    void anotherStudentsAttemptLooksLikeItDoesNotExist() throws Exception {
        Student owner = register("a@college.edu");
        Student other = register("b@college.edu");
        String id = start(owner).get("id").asText();
        String bearer = "Bearer " + other.token();
        mvc.perform(get("/mcq/attempts/" + id).header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(put("/mcq/attempts/" + id + "/answers/1").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"selected\":0}")).andExpect(status().isNotFound());
        submitRaw(other, id, sheet(id, 20)).andExpect(status().isNotFound());
        mvc.perform(get("/mcq/attempts/" + id + "/result").header("Authorization", bearer)).andExpect(status().isNotFound());
        assertThat(xp(other)).isZero();
        // the owner's test is untouched and still running
        assertThat(jdbc.queryForObject("SELECT status FROM mcq_attempts WHERE id = ?::uuid", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test
    void anExpiredTestIsGoneAndAFreshOneCanBeStarted() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        jdbc.update("UPDATE mcq_attempts SET expires_at = ? WHERE id = ?::uuid", Timestamp.from(Instant.now().minusSeconds(300)), id);
        mvc.perform(get("/mcq/attempts/" + id).header("Authorization", "Bearer " + s.token())).andExpect(status().isGone());
        submitRaw(s, id, sheet(id, 20)).andExpect(status().isGone());
        assertThat(jdbc.queryForObject("SELECT status FROM mcq_attempts WHERE id = ?::uuid", String.class, id)).isEqualTo("EXPIRED");
        assertThat(xp(s)).isZero();

        String next = start(s).get("id").asText();
        assertThat(next).isNotEqualTo(id);
    }

    @Test
    void aSlightlyLateSubmitStillCounts() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        jdbc.update("UPDATE mcq_attempts SET expires_at = ? WHERE id = ?::uuid", Timestamp.from(Instant.now().minusSeconds(20)), id);
        submit(s, id, sheet(id, 14));
    }

    @Test
    void submitWithoutABodyGradesTheSavedAnswers() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        List<Integer> sheet = sheet(id, 15);
        for (int i = 0; i < 20; i++) {
            mvc.perform(put("/mcq/attempts/" + id + "/answers/" + (i + 1)).header("Authorization", "Bearer " + s.token())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"selected\":" + sheet.get(i) + "}")).andExpect(status().isNoContent());
        }
        JsonNode r = submit(s, id, null);
        assertThat(r.get("score").asInt()).isEqualTo(15);
        assertThat(r.get("passed").asBoolean()).isTrue();
    }

    @Test
    void unansweredQuestionsCountAsWrong() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        List<Integer> blanks = new ArrayList<>();
        for (int i = 0; i < 20; i++) blanks.add(null);
        JsonNode r = submit(s, id, blanks);
        assertThat(r.get("score").asInt()).isZero();
        assertThat(r.get("review").get(0).get("selected").isNull()).isTrue();
    }

    @Test
    void badInputIsRejected() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        submitRaw(s, id, List.of(1, 2, 3)).andExpect(status().isBadRequest());
        List<Integer> outOfRange = new ArrayList<>(sheet(id, 20));
        outOfRange.set(4, 7);
        submitRaw(s, id, outOfRange).andExpect(status().isBadRequest());
        mvc.perform(put("/mcq/attempts/" + id + "/answers/1").header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"selected\":4}")).andExpect(status().isBadRequest());
        mvc.perform(put("/mcq/attempts/" + id + "/answers/21").header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"selected\":1}")).andExpect(status().isNotFound());
        // the rejected submits left the test running
        assertThat(jdbc.queryForObject("SELECT status FROM mcq_attempts WHERE id = ?::uuid", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test
    void resultIsNotAvailableBeforeSubmitting() throws Exception {
        Student s = register("a@college.edu");
        String id = start(s).get("id").asText();
        mvc.perform(get("/mcq/attempts/" + id + "/result").header("Authorization", "Bearer " + s.token())).andExpect(status().isConflict());
    }
}
