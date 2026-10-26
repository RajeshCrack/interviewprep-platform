package com.gateready.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE users CASCADE"); // topic_progress follows; courses and topics are seed data and stay
    }

    private record Student(String token, UUID id) {}

    private Student register(String email) throws Exception {
        String res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Test Student", "email", email, "password", "password1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(res);
        return new Student(n.get("accessToken").asText(), UUID.fromString(n.at("/user/id").asText()));
    }

    private JsonNode getJson(String path, Student s) throws Exception {
        return json.readTree(mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID topicId(int position) {
        return jdbc.queryForObject("SELECT t.id FROM topics t JOIN courses c ON c.id = t.course_id WHERE c.slug = 'java' AND t.position = ?",
                UUID.class, position);
    }

    private void pass(Student s, int position, boolean mcq, boolean coding) {
        jdbc.update("INSERT INTO topic_progress (user_id, topic_id, mcq_passed_at, coding_passed_at) VALUES (?, ?, ?, ?)",
                s.id(), topicId(position), mcq ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                coding ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }

    @Test
    void catalogListsJavaFirstAndMarksOtherCoursesUnpublished() throws Exception {
        Student s = register("a@college.edu");
        JsonNode c = getJson("/courses", s);
        assertThat(c.get(0).get("slug").asText()).isEqualTo("java");
        assertThat(c.get(0).get("published").asBoolean()).isTrue();
        assertThat(c.get(0).get("topicCount").asInt()).isEqualTo(10);
        assertThat(c.size()).isEqualTo(7);
        assertThat(c.get(1).get("published").asBoolean()).isFalse();
    }

    @Test
    void catalogAndRoadmapNeedALogin() throws Exception {
        mvc.perform(get("/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/courses/java/roadmap")).andExpect(status().isUnauthorized());
        mvc.perform(get("/topics/" + topicId(1))).andExpect(status().isUnauthorized());
    }

    @Test
    void aNewStudentSeesTopicOneCurrentAndNineLocked() throws Exception {
        Student s = register("a@college.edu");
        JsonNode r = getJson("/courses/java/roadmap", s);
        assertThat(r.get("passedCount").asInt()).isZero();
        assertThat(r.get("topics").size()).isEqualTo(10);
        assertThat(r.at("/topics/0/status").asText()).isEqualTo("CURRENT");
        assertThat(r.at("/topics/0/title").asText()).isEqualTo("JVM, JDK and JRE");
        for (int i = 1; i < 10; i++) assertThat(r.at("/topics/" + i + "/status").asText()).isEqualTo("LOCKED");
    }

    @Test
    void topicOneShowsItsLessonAndTopicTwoIsLocked() throws Exception {
        Student s = register("a@college.edu");
        JsonNode t = getJson("/topics/" + topicId(1), s);
        assertThat(t.get("title").asText()).isEqualTo("JVM, JDK and JRE");
        assertThat(t.get("what").asText()).contains("bytecode");
        assertThat(t.get("exampleCode").asText()).contains("public class Hello");
        assertThat(t.get("hints").size()).isEqualTo(3);
        assertThat(t.get("videoQuery").asText()).isNotBlank();
        assertThat(t.get("nextTopicId").asText()).isEqualTo(topicId(2).toString());

        mvc.perform(get("/topics/" + topicId(2)).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Pass the previous topic to unlock this one."));
        mvc.perform(get("/topics/" + topicId(10)).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theMcqAloneDoesNotUnlockTopicTwo() throws Exception {
        Student s = register("a@college.edu");
        pass(s, 1, true, false);
        assertThat(getJson("/courses/java/roadmap", s).at("/topics/1/status").asText()).isEqualTo("LOCKED");
        mvc.perform(get("/topics/" + topicId(2)).header("Authorization", "Bearer " + s.token())).andExpect(status().isForbidden());
    }

    @Test
    void passingBothRoundsUnlocksTopicTwoAndCountsProgress() throws Exception {
        Student s = register("a@college.edu");
        pass(s, 1, true, true);
        JsonNode r = getJson("/courses/java/roadmap", s);
        assertThat(r.get("passedCount").asInt()).isEqualTo(1);
        assertThat(r.at("/topics/0/status").asText()).isEqualTo("PASSED");
        assertThat(r.at("/topics/1/status").asText()).isEqualTo("CURRENT");
        assertThat(r.at("/topics/2/status").asText()).isEqualTo("LOCKED");
        JsonNode t2 = getJson("/topics/" + topicId(2), s);
        assertThat(t2.get("hasContent").asBoolean()).isFalse();
        assertThat(t2.get("position").asInt()).isEqualTo(2);
    }

    @Test
    void oneStudentsProgressNeverUnlocksTopicsForAnother() throws Exception {
        Student a = register("a@college.edu");
        Student b = register("b@college.edu");
        pass(a, 1, true, true);
        pass(a, 2, true, true);
        assertThat(getJson("/courses/java/roadmap", a).get("passedCount").asInt()).isEqualTo(2);
        JsonNode rb = getJson("/courses/java/roadmap", b);
        assertThat(rb.get("passedCount").asInt()).isZero();
        assertThat(rb.at("/topics/1/status").asText()).isEqualTo("LOCKED");
        mvc.perform(get("/topics/" + topicId(3)).header("Authorization", "Bearer " + b.token())).andExpect(status().isForbidden());
        mvc.perform(get("/topics/" + topicId(3)).header("Authorization", "Bearer " + a.token())).andExpect(status().isOk());
    }

    @Test
    void unknownAndUnpublishedThingsAreNotFound() throws Exception {
        Student s = register("a@college.edu");
        mvc.perform(get("/topics/" + UUID.randomUUID()).header("Authorization", "Bearer " + s.token())).andExpect(status().isNotFound());
        mvc.perform(get("/courses/nope/roadmap").header("Authorization", "Bearer " + s.token())).andExpect(status().isNotFound());
        mvc.perform(get("/courses/python/roadmap").header("Authorization", "Bearer " + s.token())).andExpect(status().isNotFound());
        mvc.perform(get("/topics/not-a-uuid").header("Authorization", "Bearer " + s.token())).andExpect(status().isBadRequest());
    }

    @Test
    void thereIsNoEndpointThatLetsAClientMarkATopicPassed() throws Exception {
        Student s = register("a@college.edu");
        for (String path : new String[] {"/topics/" + topicId(1) + "/complete", "/progress", "/courses/java/progress"}) {
            mvc.perform(post(path).header("Authorization", "Bearer " + s.token()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mcqPassed\":true,\"codingPassed\":true}")).andExpect(status().is4xxClientError());
        }
        assertThat(getJson("/courses/java/roadmap", s).get("passedCount").asInt()).isZero();
    }
}
