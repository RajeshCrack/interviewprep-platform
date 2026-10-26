package com.gateready.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateready.MutableClock;
import com.gateready.engage.EmailSender;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiTest {
    private static final Instant START = Instant.parse("2026-10-26T10:00:00Z");
    private static final Pattern LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    static class Outbox implements EmailSender {
        record Sent(String to, String subject, String body) {}
        final List<Sent> sent = new ArrayList<>();
        boolean down;

        @Override public void send(String to, String subject, String body) {
            if (down) throw new IllegalStateException("smtp down");
            sent.add(new Sent(to, subject, body));
        }

        Sent last() { return sent.get(sent.size() - 1); }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary MutableClock testClock() { return new MutableClock(START, ZoneId.of("Asia/Kolkata")); }
        @Bean @Primary Outbox outbox() { return new Outbox(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired Outbox outbox;
    @Autowired JwtService jwt;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE users CASCADE");
        // The per-IP limiter lives across tests, so each test starts well after the last one's window.
        if (clock.instant().isBefore(START)) clock.set(START);
        clock.advance(Duration.ofHours(2));
        outbox.sent.clear();
        outbox.down = false;
    }

    private MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode register(String email) throws Exception {
        return json.readTree(mvc.perform(jsonPost("/auth/register", Map.of("name", "Aarav Sharma", "email", email, "password", "password1")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String tokenIn(Outbox.Sent mail) {
        Matcher m = LINK.matcher(mail.body());
        assertThat(m.find()).as("a link with a token in: " + mail.body()).isTrue();
        return m.group(1);
    }

    private boolean verified(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE email = ?", Boolean.class, email));
    }

    private void forgot(String email, int expected) throws Exception {
        mvc.perform(jsonPost("/auth/forgot-password", Map.of("email", email))).andExpect(status().is(expected));
    }

    private void reset(String token, String password, int expected) throws Exception {
        mvc.perform(jsonPost("/auth/reset-password", Map.of("token", token, "newPassword", password))).andExpect(status().is(expected));
    }

    private void login(String email, String password, int expected) throws Exception {
        mvc.perform(jsonPost("/auth/login", Map.of("email", email, "password", password))).andExpect(status().is(expected));
    }

    // ---- verification ----

    @Test
    void registeringSendsAVerificationLinkAndStartsUnverified() throws Exception {
        JsonNode res = register("a@college.edu");
        assertThat(res.at("/user/emailVerified").asBoolean()).isFalse();
        assertThat(outbox.sent).hasSize(1);
        assertThat(outbox.last().to()).isEqualTo("a@college.edu");
        assertThat(outbox.last().body()).contains("/verify-email?token=");
        assertThat(verified("a@college.edu")).isFalse();
    }

    @Test
    void theLinkVerifiesOnceAndOnlyOnce() throws Exception {
        register("a@college.edu");
        String token = tokenIn(outbox.last());
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", token))).andExpect(status().isNoContent());
        assertThat(verified("a@college.edu")).isTrue();
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", token))).andExpect(status().isBadRequest());
    }

    @Test
    void garbageExpiredAndWrongKindTokensAreRejected() throws Exception {
        register("a@college.edu");
        String verifyToken = tokenIn(outbox.last());
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", "not-a-real-token"))).andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", ""))).andExpect(status().isBadRequest());
        reset(verifyToken, "newpassword1", 400); // a verification link cannot reset a password
        clock.advance(Duration.ofHours(49));
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", verifyToken))).andExpect(status().isBadRequest());
        assertThat(verified("a@college.edu")).isFalse();
    }

    @Test
    void resendSendsANewLinkAndTheOldOneStopsWorking() throws Exception {
        JsonNode res = register("a@college.edu");
        String first = tokenIn(outbox.last());
        String auth = "Bearer " + res.get("accessToken").asText();
        clock.advance(Duration.ofSeconds(61));
        mvc.perform(post("/me/resend-verification").header("Authorization", auth)).andExpect(status().isNoContent());
        assertThat(outbox.sent).hasSize(2);
        String second = tokenIn(outbox.last());
        assertThat(second).isNotEqualTo(first);
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", first))).andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", second))).andExpect(status().isNoContent());
    }

    @Test
    void resendIsThrottledToOnePerMinuteAndNeedsLogin() throws Exception {
        JsonNode res = register("a@college.edu");
        String auth = "Bearer " + res.get("accessToken").asText();
        mvc.perform(post("/me/resend-verification").header("Authorization", auth)).andExpect(status().isTooManyRequests());
        assertThat(outbox.sent).hasSize(1);
        mvc.perform(post("/me/resend-verification")).andExpect(status().isUnauthorized());
    }

    @Test
    void resendForAVerifiedStudentSendsNothing() throws Exception {
        JsonNode res = register("a@college.edu");
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", tokenIn(outbox.last())))).andExpect(status().isNoContent());
        outbox.sent.clear();
        clock.advance(Duration.ofMinutes(5));
        String auth = "Bearer " + jwt.issue(UUID.fromString(res.at("/user/id").asText()), "STUDENT");
        mvc.perform(post("/me/resend-verification").header("Authorization", auth)).andExpect(status().isNoContent());
        assertThat(outbox.sent).isEmpty();
    }

    @Test
    void aBrokenMailServerDoesNotBreakRegistration() throws Exception {
        outbox.down = true;
        register("a@college.edu");
        assertThat(outbox.sent).isEmpty();
        login("a@college.edu", "password1", 200);
    }

    // ---- password reset ----

    @Test
    void forgotPasswordLooksTheSameForKnownAndUnknownAddresses() throws Exception {
        register("a@college.edu");
        outbox.sent.clear();
        String known = mvc.perform(jsonPost("/auth/forgot-password", Map.of("email", "a@college.edu")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(jsonPost("/auth/forgot-password", Map.of("email", "nobody@college.edu")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);
        assertThat(outbox.sent).hasSize(1);
        assertThat(outbox.last().to()).isEqualTo("a@college.edu");
    }

    @Test
    void forgotPasswordIsCaseInsensitiveAndValidatesTheAddress() throws Exception {
        register("a@college.edu");
        outbox.sent.clear();
        forgot("A@College.EDU", 202);
        assertThat(outbox.sent).hasSize(1);
        mvc.perform(jsonPost("/auth/forgot-password", Map.of("email", "not-an-email"))).andExpect(status().isBadRequest());
    }

    @Test
    void theResetFlowChangesThePasswordSignsOutEverywhereAndWorksOnce() throws Exception {
        JsonNode res = register("a@college.edu");
        String refresh = res.get("refreshToken").asText();
        outbox.sent.clear();
        forgot("a@college.edu", 202);
        String token = tokenIn(outbox.last());
        outbox.sent.clear();

        reset(token, "brand-new-pass", 204);

        login("a@college.edu", "password1", 401);
        login("a@college.edu", "brand-new-pass", 200);
        mvc.perform(jsonPost("/auth/refresh", Map.of("refreshToken", refresh))).andExpect(status().isUnauthorized());
        assertThat(outbox.sent).hasSize(1);
        assertThat(outbox.last().subject()).contains("password was changed");
        reset(token, "another-pass-1", 400); // single use
        login("a@college.edu", "brand-new-pass", 200);
    }

    @Test
    void resettingProvesTheInboxSoTheAddressBecomesVerified() throws Exception {
        register("a@college.edu");
        assertThat(verified("a@college.edu")).isFalse();
        forgot("a@college.edu", 202);
        reset(tokenIn(outbox.last()), "brand-new-pass", 204);
        assertThat(verified("a@college.edu")).isTrue();
    }

    @Test
    void aShortOrMissingNewPasswordIsRejectedAndKeepsTheLinkUsable() throws Exception {
        register("a@college.edu");
        forgot("a@college.edu", 202);
        String token = tokenIn(outbox.last());
        reset(token, "short", 400);
        mvc.perform(jsonPost("/auth/reset-password", Map.of("token", token, "newPassword", "x".repeat(73)))).andExpect(status().isBadRequest());
        login("a@college.edu", "password1", 200);
        reset(token, "long-enough-pass", 204);
    }

    @Test
    void resetLinksExpireAfterAnHour() throws Exception {
        register("a@college.edu");
        forgot("a@college.edu", 202);
        String token = tokenIn(outbox.last());
        clock.advance(Duration.ofMinutes(61));
        reset(token, "brand-new-pass", 400);
        login("a@college.edu", "password1", 200);
    }

    @Test
    void aNewResetRequestCancelsTheEarlierLink() throws Exception {
        register("a@college.edu");
        forgot("a@college.edu", 202);
        String first = tokenIn(outbox.last());
        forgot("a@college.edu", 202);
        String second = tokenIn(outbox.last());
        reset(first, "brand-new-pass", 400);
        reset(second, "brand-new-pass", 204);
    }

    @Test
    void anAddressGetsAtMostThreeResetEmailsAnHour() throws Exception {
        register("a@college.edu");
        outbox.sent.clear();
        for (int i = 0; i < 5; i++) forgot("a@college.edu", 202);
        assertThat(outbox.sent).hasSize(3);
        clock.advance(Duration.ofMinutes(61));
        forgot("a@college.edu", 202);
        assertThat(outbox.sent).hasSize(4);
    }

    @Test
    void oneClientCannotHammerForgotPassword() throws Exception {
        for (int i = 0; i < 10; i++) forgot("nobody" + i + "@college.edu", 202);
        forgot("nobody-else@college.edu", 429);
        clock.advance(Duration.ofMinutes(16));
        forgot("nobody-else@college.edu", 202);
    }

    @Test
    void resetStillWorksWhenTheConfirmationMailFails() throws Exception {
        register("a@college.edu");
        forgot("a@college.edu", 202);
        String token = tokenIn(outbox.last());
        outbox.down = true;
        reset(token, "brand-new-pass", 204);
        login("a@college.edu", "brand-new-pass", 200);
    }

    @Test
    void theNewAccountRoutesAreOpenButMeRoutesStillNeedLogin() throws Exception {
        mvc.perform(get("/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(jsonPost("/auth/verify-email", Map.of("token", "x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
