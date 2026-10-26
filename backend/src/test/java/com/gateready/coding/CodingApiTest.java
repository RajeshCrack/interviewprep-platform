package com.gateready.coding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class CodingApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private static final Map<Integer, String> REFERENCE = new HashMap<>();

    @BeforeEach
    void clean() throws IOException {
        jdbc.execute("TRUNCATE users CASCADE");
        if (REFERENCE.isEmpty()) {
            for (int i = 1; i <= 5; i++) {
                try (var in = getClass().getResourceAsStream("/coding-reference/p" + i + ".txt")) {
                    REFERENCE.put(i, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
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

    private UUID problemId(int position) {
        return jdbc.queryForObject("SELECT id FROM coding_problems WHERE topic_id = ? AND position = ?", UUID.class, topicId(1), position);
    }

    /** Opens the coding round the way a passed MCQ test does. */
    private void passMcq(Student s) {
        jdbc.update("INSERT INTO topic_progress (user_id, topic_id, mcq_passed_at) VALUES (?, ?, ?)", s.id(), topicId(1), Timestamp.from(Instant.now()));
    }

    private ResultActions roundRaw(Student s) throws Exception {
        return mvc.perform(get("/topics/" + topicId(1) + "/coding").header("Authorization", "Bearer " + s.token()));
    }

    private JsonNode round(Student s) throws Exception {
        return json.readTree(roundRaw(s).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions submitRaw(Student s, UUID problem, String code, String mode) throws Exception {
        return mvc.perform(post("/coding/problems/" + problem + "/submissions").header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code, "mode", mode))));
    }

    private JsonNode submit(Student s, int position, String code, String mode) throws Exception {
        return json.readTree(submitRaw(s, problemId(position), code, mode).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode solve(Student s, int position) throws Exception {
        JsonNode r = submit(s, position, REFERENCE.get(position), "SUBMIT");
        assertThat(r.get("status").asText()).as("reference solution " + position).isEqualTo("ACCEPTED");
        return r;
    }

    private int xp(Student s) {
        Integer v = jdbc.query("SELECT xp FROM user_stats WHERE user_id = ?", rs -> rs.next() ? rs.getInt(1) : 0, s.id());
        return v == null ? 0 : v;
    }

    @Test
    void needsALogin() throws Exception {
        mvc.perform(get("/topics/" + topicId(1) + "/coding")).andExpect(status().isUnauthorized());
        mvc.perform(post("/coding/problems/" + problemId(1) + "/submissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theRoundIsClosedUntilTheMcqTestIsPassed() throws Exception {
        Student s = register("a@college.edu");
        roundRaw(s).andExpect(status().isForbidden());
        submitRaw(s, problemId(1), REFERENCE.get(1), "SUBMIT").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coding_submissions", Integer.class)).isZero();
        mvc.perform(get("/topics/" + topicId(2) + "/coding").header("Authorization", "Bearer " + s.token())).andExpect(status().isForbidden());
        mvc.perform(get("/topics/" + UUID.randomUUID() + "/coding").header("Authorization", "Bearer " + s.token())).andExpect(status().isNotFound());
    }

    @Test
    void theRoundListsFiveProblemsWithOnlySampleTests() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        String raw = roundRaw(s).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(raw);
        assertThat(r.get("problems")).hasSize(5);
        assertThat(r.get("problems").findValuesAsText("difficulty")).containsExactly("EASY", "EASY", "MEDIUM", "HARD", "HARD");
        assertThat(r.get("neededTotal").asInt()).isEqualTo(3);
        assertThat(r.get("totalEasy").asInt()).isEqualTo(2);
        for (JsonNode p : r.get("problems")) {
            assertThat(p.get("starterCode").asText()).contains("class Main");
            assertThat(p.get("samples").size()).isBetween(1, 2);
            assertThat(p.get("solved").asBoolean()).isFalse();
        }
        // the large hidden inputs are not in the response at all
        assertThat(raw.length()).isLessThan(20_000);
        List<String> hiddenInputs = jdbc.query("SELECT input FROM coding_tests WHERE NOT visible", (rs, i) -> rs.getString(1));
        assertThat(hiddenInputs).isNotEmpty();
        hiddenInputs.stream().filter(h -> h.length() > 60).forEach(h -> assertThat(raw).doesNotContain(h));
    }

    @Test
    void everyReferenceSolutionPassesEveryTestIncludingHiddenOnes() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        for (int p = 1; p <= 5; p++) {
            JsonNode r = solve(s, p);
            int total = jdbc.queryForObject("SELECT count(*) FROM coding_tests WHERE problem_id = ?", Integer.class, problemId(p));
            assertThat(r.get("passedTests").asInt()).isEqualTo(total);
            assertThat(r.get("totalTests").asInt()).isEqualTo(total);
            assertThat(r.get("solved").asBoolean()).isTrue();
        }
    }

    @Test
    void hiddenTestsShowPassOrFailAndNothingElse() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        JsonNode r = submit(s, 1, REFERENCE.get(1).replace("long sum = 0", "int sum = 0"), "SUBMIT"); // passes the samples, overflows on the big hidden tests
        assertThat(r.get("status").asText()).isEqualTo("WRONG_ANSWER");
        boolean sawHidden = false;
        for (JsonNode t : r.get("tests")) {
            if (!t.get("visible").asBoolean()) {
                sawHidden = true;
                assertThat(t.get("input").isNull()).isTrue();
                assertThat(t.get("expected").isNull()).isTrue();
                assertThat(t.get("actual").isNull()).isTrue();
            } else {
                assertThat(t.get("expected").asText()).isNotBlank();
            }
        }
        assertThat(sawHidden).isTrue();
    }

    @Test
    void aWrongAnswerFailsAndDoesNotSolveTheProblem() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        JsonNode r = submit(s, 2, REFERENCE.get(2).replace("\"aeiou\"", "\"aeio\""), "SUBMIT");
        assertThat(r.get("status").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(r.get("passedTests").asInt()).isLessThan(r.get("totalTests").asInt());
        assertThat(r.get("solved").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coding_solved", Integer.class)).isZero();
    }

    @Test
    void aCompileErrorShowsTheCompilerMessage() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        JsonNode r = submit(s, 1, "public class Main { public static void main(String[] a) { int x = ; } }", "SUBMIT");
        assertThat(r.get("status").asText()).isEqualTo("COMPILE_ERROR");
        assertThat(r.get("message").asText()).contains("error");
        assertThat(r.get("passedTests").asInt()).isZero();
    }

    @Test
    void aRuntimeCrashAndAnEndlessLoopAreReportedAsSuch() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        assertThat(submit(s, 1, "public class Main { public static void main(String[] a) { throw new RuntimeException(\"x\"); } }", "RUN")
                .get("status").asText()).isEqualTo("RUNTIME_ERROR");
        JsonNode slow = submit(s, 1, "public class Main { public static void main(String[] a) { while (true) {} } }", "RUN");
        assertThat(slow.get("status").asText()).isEqualTo("TIME_LIMIT");
    }

    @Test
    void runChecksOnlySamplesAndNeverSolvesAProblem() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        JsonNode r = submit(s, 1, REFERENCE.get(1), "RUN");
        assertThat(r.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(r.get("totalTests").asInt()).isEqualTo(2); // only the visible tests
        assertThat(r.get("solved").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coding_solved", Integer.class)).isZero();
    }

    @Test
    void theLastSubmittedCodeComesBackWhenTheStudentReturns() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        submit(s, 3, "public class Main { public static void main(String[] a) { System.out.println(\"draft\"); } }", "RUN");
        JsonNode p3 = round(s).get("problems").get(2);
        assertThat(p3.get("lastCode").asText()).contains("draft");
        assertThat(round(s).get("problems").get(0).get("lastCode").isNull()).isTrue();
    }

    @Test
    void thePassRuleNeedsBothEasyProblemsAndThreeInTotal() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        // one easy + medium + hard = three solved, but easy 2 is missing
        solve(s, 1); solve(s, 3);
        JsonNode third = solve(s, 4);
        assertThat(third.get("roundPassed").asBoolean()).isFalse();
        assertThat(round(s).get("solvedCount").asInt()).isEqualTo(3);
        assertThat(round(s).get("solvedEasy").asInt()).isEqualTo(1);
        assertThat(xp(s)).isZero();
        assertThat(jdbc.queryForObject("SELECT coding_passed_at IS NULL FROM topic_progress WHERE user_id = ?", Boolean.class, s.id())).isTrue();

        // the second easy problem completes the rule
        JsonNode done = solve(s, 2);
        assertThat(done.get("roundPassed").asBoolean()).isTrue();
        assertThat(done.get("xpAwarded").asInt()).isEqualTo(200);
        assertThat(xp(s)).isEqualTo(200);
    }

    @Test
    void passingTheRoundUnlocksTheNextTopicAndPaysXpOnlyOnce() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        mvc.perform(get("/topics/" + topicId(2)).header("Authorization", "Bearer " + s.token())).andExpect(status().isForbidden());
        solve(s, 1); solve(s, 2);
        JsonNode third = solve(s, 3);
        assertThat(third.get("roundPassed").asBoolean()).isTrue();
        assertThat(third.get("xpAwarded").asInt()).isEqualTo(200);

        JsonNode roadmap = json.readTree(mvc.perform(get("/courses/java/roadmap").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(roadmap.at("/topics/0/status").asText()).isEqualTo("PASSED");
        assertThat(roadmap.at("/topics/1/status").asText()).isEqualTo("CURRENT");
        mvc.perform(get("/topics/" + topicId(2)).header("Authorization", "Bearer " + s.token())).andExpect(status().isOk());

        // more solved problems and repeat submits pay nothing more
        assertThat(solve(s, 4).get("xpAwarded").asInt()).isZero();
        assertThat(solve(s, 1).get("xpAwarded").asInt()).isZero();
        assertThat(xp(s)).isEqualTo(200);
        assertThat(round(s).get("passed").asBoolean()).isTrue();
    }

    @Test
    void oneStudentsProgressDoesNotTouchAnother() throws Exception {
        Student a = register("a@college.edu");
        Student b = register("b@college.edu");
        passMcq(a); passMcq(b);
        solve(a, 1);
        assertThat(round(a).get("solvedCount").asInt()).isEqualTo(1);
        assertThat(round(b).get("solvedCount").asInt()).isZero();
        assertThat(round(b).get("problems").get(0).get("lastCode").isNull()).isTrue();
    }

    @Test
    void badInputIsRejected() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        submitRaw(s, problemId(1), "   ", "RUN").andExpect(status().isBadRequest());
        submitRaw(s, problemId(1), "x".repeat(CodingService.MAX_CODE_CHARS + 1), "RUN").andExpect(status().isBadRequest());
        submitRaw(s, problemId(1), REFERENCE.get(1), "PRACTICE").andExpect(status().isBadRequest());
        submitRaw(s, UUID.randomUUID(), REFERENCE.get(1), "RUN").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coding_submissions", Integer.class)).isZero();
    }

    @Test
    void tooManyRunsInAMinuteAreRefused() throws Exception {
        Student s = register("a@college.edu");
        passMcq(s);
        for (int i = 0; i < CodingService.MAX_SUBMISSIONS_PER_MINUTE; i++) {
            jdbc.update("INSERT INTO coding_submissions (id, user_id, problem_id, mode, code, status, passed_tests, total_tests, created_at) VALUES (?, ?, ?, 'RUN', 'x', 'WRONG_ANSWER', 0, 2, ?)",
                    UUID.randomUUID(), s.id(), problemId(1), Timestamp.from(Instant.now()));
        }
        submitRaw(s, problemId(1), REFERENCE.get(1), "RUN").andExpect(status().isTooManyRequests());
        // another student is not affected
        Student other = register("b@college.edu");
        passMcq(other);
        assertThat(submit(other, 1, REFERENCE.get(1), "RUN").get("status").asText()).isEqualTo("ACCEPTED");
    }
}
