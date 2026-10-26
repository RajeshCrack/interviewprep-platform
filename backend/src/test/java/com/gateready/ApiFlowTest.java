package com.gateready;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-10-26T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE users CASCADE");
        clock.set(Instant.parse("2026-10-26T06:00:00Z"));
    }

    // ---- helpers

    private ResultActions postJson(String path, Object body, String token) throws Exception {
        var r = MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) r.header("Authorization", "Bearer " + token);
        return mvc.perform(r).andDo(x -> {});
    }

    private JsonNode body(ResultActions a) throws Exception {
        return json.readTree(a.andReturn().getResponse().getContentAsString());
    }

    private JsonNode register(String name, String email) throws Exception {
        return body(postJson("/auth/register", Map.of("name", name, "email", email, "password", "password1"), null)
                .andExpect(status().isCreated()));
    }

    private JsonNode login(String email) throws Exception {
        return body(postJson("/auth/login", Map.of("email", email, "password", "password1"), null).andExpect(status().isOk()));
    }

    private ResultActions authedGet(String path, String token) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private JsonNode dashboard(String token) throws Exception {
        return body(authedGet("/dashboard", token).andExpect(status().isOk()));
    }

    private JsonNode logStudy(String token, int minutes) throws Exception {
        return body(postJson("/me/study-log", Map.of("minutes", minutes), token).andExpect(status().isOk()));
    }

    // ---- registration and login

    @Test
    void registerReturnsTokensAndTheUser() throws Exception {
        JsonNode t = register("Aarav Sharma", "Aarav@College.edu");
        assertThat(t.get("accessToken").asText()).isNotBlank();
        assertThat(t.get("refreshToken").asText()).isNotBlank();
        assertThat(t.at("/user/email").asText()).isEqualTo("aarav@college.edu");
        assertThat(t.at("/user/name").asText()).isEqualTo("Aarav Sharma");
    }

    @Test
    void passwordsAreStoredHashedAndRefreshTokensAreNotStoredInTheClear() throws Exception {
        JsonNode t = register("Aarav Sharma", "aarav@college.edu");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("password1");
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class,
                t.get("refreshToken").asText());
        assertThat(rows).isZero();
    }

    @Test
    void registrationRejectsDuplicateEmailsInAnyCase() throws Exception {
        register("A One", "dup@college.edu");
        postJson("/auth/register", Map.of("name", "A Two", "email", "DUP@college.edu", "password", "password1"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That email is already registered."));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        postJson("/auth/register", Map.of("name", "A", "email", "a@b.co", "password", "password1"), null)
                .andExpect(status().isBadRequest());
        postJson("/auth/register", Map.of("name", "Aarav", "email", "not-an-email", "password", "password1"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address."));
        postJson("/auth/register", Map.of("name", "Aarav", "email", "a@b.co", "password", "short"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWorksWithAnyEmailCaseAndFailsWithAWrongPassword() throws Exception {
        register("Aarav Sharma", "aarav@college.edu");
        postJson("/auth/login", Map.of("email", "AARAV@college.edu", "password", "password1"), null).andExpect(status().isOk());
        postJson("/auth/login", Map.of("email", "aarav@college.edu", "password", "wrong-pass"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
        postJson("/auth/login", Map.of("email", "nobody@college.edu", "password", "password1"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void sixthWrongPasswordIsRateLimited() throws Exception {
        register("Aarav Sharma", "aarav@college.edu");
        for (int i = 0; i < 5; i++) {
            postJson("/auth/login", Map.of("email", "aarav@college.edu", "password", "wrong-pass"), null)
                    .andExpect(status().isUnauthorized());
        }
        postJson("/auth/login", Map.of("email", "aarav@college.edu", "password", "password1"), null)
                .andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofMinutes(16));
        postJson("/auth/login", Map.of("email", "aarav@college.edu", "password", "password1"), null)
                .andExpect(status().isOk());
    }

    // ---- tokens

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
        authedGet("/dashboard", "garbage").andExpect(status().isUnauthorized());
        String token = register("Aarav Sharma", "aarav@college.edu").get("accessToken").asText();
        authedGet("/dashboard", token + "x").andExpect(status().isUnauthorized());
        authedGet("/dashboard", token).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anExpiredAccessTokenIsRejected() throws Exception {
        String token = register("Aarav Sharma", "aarav@college.edu").get("accessToken").asText();
        clock.advance(Duration.ofMinutes(16));
        authedGet("/dashboard", token).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheTokenAndTheOldOneStopsWorking() throws Exception {
        JsonNode first = register("Aarav Sharma", "aarav@college.edu");
        String r1 = first.get("refreshToken").asText();
        JsonNode second = body(postJson("/auth/refresh", Map.of("refreshToken", r1), null).andExpect(status().isOk()));
        String r2 = second.get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);
        authedGet("/dashboard", second.get("accessToken").asText()).andExpect(status().isOk());
        postJson("/auth/refresh", Map.of("refreshToken", r1), null).andExpect(status().isUnauthorized());
    }

    @Test
    void reusingAnOldRefreshTokenRevokesTheWholeSession() throws Exception {
        String r1 = register("Aarav Sharma", "aarav@college.edu").get("refreshToken").asText();
        String r2 = body(postJson("/auth/refresh", Map.of("refreshToken", r1), null)).get("refreshToken").asText();
        postJson("/auth/refresh", Map.of("refreshToken", r1), null).andExpect(status().isUnauthorized());
        postJson("/auth/refresh", Map.of("refreshToken", r2), null).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String r1 = register("Aarav Sharma", "aarav@college.edu").get("refreshToken").asText();
        postJson("/auth/logout", Map.of("refreshToken", r1), null).andExpect(status().isNoContent());
        postJson("/auth/refresh", Map.of("refreshToken", r1), null).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredRefreshTokenIsRejected() throws Exception {
        String r1 = register("Aarav Sharma", "aarav@college.edu").get("refreshToken").asText();
        clock.advance(Duration.ofDays(31));
        postJson("/auth/refresh", Map.of("refreshToken", r1), null).andExpect(status().isUnauthorized());
    }

    // ---- ownership: one student can never see or change another's data

    @Test
    void eachStudentSeesOnlyTheirOwnGoalProgressAndIdentity() throws Exception {
        String a = register("Student A", "a@college.edu").get("accessToken").asText();
        String b = register("Student B", "b@college.edu").get("accessToken").asText();

        putJson("/me/goal", a, Map.of("hoursPerDay", 4.0, "reminderTime", "07:00", "channel", "EMAIL"));
        logStudy(a, 90);

        JsonNode da = dashboard(a);
        JsonNode db = dashboard(b);
        assertThat(da.at("/user/email").asText()).isEqualTo("a@college.edu");
        assertThat(da.at("/goal/hoursPerDay").asDouble()).isEqualTo(4.0);
        assertThat(da.get("todayMinutes").asInt()).isEqualTo(90);
        assertThat(db.at("/user/email").asText()).isEqualTo("b@college.edu");
        assertThat(db.at("/goal/hoursPerDay").asDouble()).isEqualTo(2.0);
        assertThat(db.at("/goal/channel").asText()).isEqualTo("PUSH");
        assertThat(db.get("todayMinutes").asInt()).isZero();
        assertThat(db.get("xp").asInt()).isZero();
    }

    @Test
    void aUserIdInTheRequestBodyCannotRedirectAWrite() throws Exception {
        String a = register("Student A", "a@college.edu").get("accessToken").asText();
        String b = register("Student B", "b@college.edu").get("accessToken").asText();
        String idOfA = body(authedGet("/me", a)).at("/user/id").asText();
        mvc.perform(MockMvcRequestBuilders.post("/me/study-log").header("Authorization", "Bearer " + b)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":30,\"userId\":\"" + idOfA + "\"}")).andExpect(status().isOk());
        assertThat(dashboard(a).get("todayMinutes").asInt()).isZero();
        assertThat(dashboard(b).get("todayMinutes").asInt()).isEqualTo(30);
    }

    // ---- goals

    private ResultActions putJson(String path, String token, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void goalIsSavedAndValidated() throws Exception {
        String t = register("Aarav Sharma", "aarav@college.edu").get("accessToken").asText();
        putJson("/me/goal", t, Map.of("hoursPerDay", 3.5, "reminderTime", "06:45", "channel", "EMAIL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hoursPerDay").value(3.5));
        assertThat(dashboard(t).at("/goal/reminderTime").asText()).isEqualTo("06:45");
        putJson("/me/goal", t, Map.of("hoursPerDay", 5, "reminderTime", "06:45", "channel", "EMAIL")).andExpect(status().isBadRequest());
        putJson("/me/goal", t, Map.of("hoursPerDay", 0.1, "reminderTime", "06:45", "channel", "EMAIL")).andExpect(status().isBadRequest());
        putJson("/me/goal", t, Map.of("hoursPerDay", 2, "reminderTime", "25:00", "channel", "EMAIL")).andExpect(status().isBadRequest());
        putJson("/me/goal", t, Map.of("hoursPerDay", 2, "reminderTime", "20:00", "channel", "SMS")).andExpect(status().isBadRequest());
        assertThat(dashboard(t).at("/goal/hoursPerDay").asDouble()).isEqualTo(3.5);
    }

    // ---- study log, streak and XP

    @Test
    void meetingTheGoalRaisesTheStreakOnceAndAwardsXp() throws Exception {
        String t = register("Aarav Sharma", "aarav@college.edu").get("accessToken").asText();
        putJson("/me/goal", t, Map.of("hoursPerDay", 0.5, "reminderTime", "20:00", "channel", "PUSH"));
        JsonNode d = logStudy(t, 15);
        assertThat(d.get("streak").asInt()).isZero();
        assertThat(d.get("xp").asInt()).isEqualTo(3);
        d = logStudy(t, 15);
        assertThat(d.get("todayMinutes").asInt()).isEqualTo(30);
        assertThat(d.get("streak").asInt()).isEqualTo(1);
        assertThat(d.get("xp").asInt()).isEqualTo(3 + 3 + 50);
        d = logStudy(t, 60);
        assertThat(d.get("streak").asInt()).isEqualTo(1);
        assertThat(d.get("xp").asInt()).isEqualTo(3 + 3 + 50 + 12);
    }

    @Test
    void streakGrowsOnConsecutiveDaysAndResetsAfterAMissedDay() throws Exception {
        register("Aarav Sharma", "aarav@college.edu");
        String t = login("aarav@college.edu").get("accessToken").asText();
        putJson("/me/goal", t, Map.of("hoursPerDay", 0.5, "reminderTime", "20:00", "channel", "PUSH"));
        assertThat(logStudy(t, 30).get("streak").asInt()).isEqualTo(1);

        clock.advance(Duration.ofDays(1));
        t = login("aarav@college.edu").get("accessToken").asText();
        assertThat(dashboard(t).get("streak").asInt()).isEqualTo(1);
        assertThat(dashboard(t).get("todayMinutes").asInt()).isZero();
        assertThat(logStudy(t, 30).get("streak").asInt()).isEqualTo(2);

        clock.advance(Duration.ofDays(3));
        t = login("aarav@college.edu").get("accessToken").asText();
        assertThat(dashboard(t).get("streak").asInt()).isZero();
        assertThat(logStudy(t, 30).get("streak").asInt()).isEqualTo(1);
    }

    @Test
    void todayFollowsTheConfiguredZoneNotUtc() throws Exception {
        // 19:00 UTC on Oct 26 is 00:30 on Oct 27 in Asia/Kolkata
        clock.set(Instant.parse("2026-10-26T19:00:00Z"));
        String t = register("Aarav Sharma", "aarav@college.edu").get("accessToken").asText();
        logStudy(t, 20);
        assertThat(jdbc.queryForObject("SELECT day::text FROM study_logs", String.class)).isEqualTo("2026-10-27");
    }

    @Test
    void studyLogRejectsOutOfRangeMinutes() throws Exception {
        String t = register("Aarav Sharma", "aarav@college.edu").get("accessToken").asText();
        postJson("/me/study-log", Map.of("minutes", 0), t).andExpect(status().isBadRequest());
        postJson("/me/study-log", Map.of("minutes", -5), t).andExpect(status().isBadRequest());
        postJson("/me/study-log", Map.of("minutes", 241), t).andExpect(status().isBadRequest());
    }
}
