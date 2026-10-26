package com.gateready.engage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateready.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EngageApiTest {
    /** 19:30 in India, half an hour before the default 20:00 reminder. */
    private static final Instant START = Instant.parse("2026-10-26T14:00:00Z");

    static class RecordingEmail implements EmailSender {
        record Sent(String to, String subject, String body) {}
        final List<Sent> sent = new ArrayList<>();
        String failFor;

        @Override public void send(String to, String subject, String body) {
            if (to.equals(failFor)) throw new IllegalStateException("smtp down");
            sent.add(new Sent(to, subject, body));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary MutableClock testClock() { return new MutableClock(START, ZoneId.of("Asia/Kolkata")); }
        @Bean @Primary RecordingEmail recordingEmail() { return new RecordingEmail(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired RecordingEmail email;
    @Autowired ReminderService reminders;
    @Autowired BadgeService badges;
    @Autowired com.gateready.auth.JwtService jwt;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE users CASCADE");
        clock.set(START);
        email.sent.clear();
        email.failFor = null;
    }

    private record Student(String token, UUID id, String email) {}

    /** Registers a student whose email is already verified, with the verification mail cleared from the outbox. */
    private Student register(String address) throws Exception {
        Student s = registerUnverified(address);
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", s.id());
        email.sent.clear();
        return s;
    }

    private Student registerUnverified(String address) throws Exception {
        String res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Aarav Sharma", "email", address, "password", "password1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(res);
        return new Student(n.get("accessToken").asText(), UUID.fromString(n.at("/user/id").asText()), address);
    }

    /** A fresh access token at the current test time, because the clock jumps further than a token lives. */
    private String bearer(Student s) { return "Bearer " + jwt.issue(s.id(), "STUDENT"); }

    private void setGoal(Student s, double hours, String time, String channel) throws Exception {
        mvc.perform(put("/me/goal").header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("hoursPerDay", hours, "reminderTime", time, "channel", channel)))).andExpect(status().isOk());
    }

    private void logStudy(Student s, int minutes) throws Exception {
        mvc.perform(post("/me/study-log").header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":" + minutes + "}")).andExpect(status().isOk());
    }

    private JsonNode notifications(Student s) throws Exception {
        return json.readTree(mvc.perform(get("/me/notifications").header("Authorization", bearer(s)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode badgeList(Student s) throws Exception {
        return json.readTree(mvc.perform(get("/me/badges").header("Authorization", bearer(s)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long reminderCount(Student s) throws Exception {
        return notifications(s).get("items").findValues("kind").stream().filter(k -> k.asText().equals("REMINDER")).count();
    }

    // ---- reminders ----

    @Test
    void unverifiedAddressGetsTheInAppReminderButNoEmail() throws Exception {
        Student s = registerUnverified("typo@college.edu");
        email.sent.clear();
        setGoal(s, 2, "20:00", "EMAIL");
        clock.set(START.plus(Duration.ofMinutes(31)));
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        assertThat(reminderCount(s)).isEqualTo(1);
        assertThat(email.sent).isEmpty();
    }

    @Test
    void nothingIsSentBeforeTheChosenTime() throws Exception {
        Student s = register("a@college.edu");
        assertThat(reminders.sendDueReminders()).isZero();
        assertThat(notifications(s).get("unread").asInt()).isZero();
    }

    @Test
    void aStudentWhoHasNotMetTheirGoalGetsOneReminderAtTheirTime() throws Exception {
        Student s = register("a@college.edu");
        clock.advance(Duration.ofMinutes(31)); // 20:01
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        JsonNode n = notifications(s);
        assertThat(n.get("unread").asInt()).isEqualTo(1);
        assertThat(n.at("/items/0/title").asText()).isEqualTo("Time to study, Aarav");
        assertThat(n.at("/items/0/kind").asText()).isEqualTo("REMINDER");
        assertThat(email.sent).isEmpty(); // the default channel is in-app only
    }

    @Test
    void runningTheSchedulerAgainDoesNotRepeatTheReminder() throws Exception {
        Student s = register("a@college.edu");
        clock.advance(Duration.ofMinutes(31));
        reminders.sendDueReminders();
        clock.advance(Duration.ofMinutes(1));
        assertThat(reminders.sendDueReminders()).isZero();
        assertThat(reminderCount(s)).isEqualTo(1);
    }

    @Test
    void aStudentWhoAlreadyMetTheirGoalIsLeftAlone() throws Exception {
        Student s = register("a@college.edu");
        logStudy(s, 120);
        clock.advance(Duration.ofMinutes(31));
        assertThat(reminders.sendDueReminders()).isZero();
        assertThat(reminderCount(s)).isZero();
    }

    @Test
    void anEmailStudentGetsTheEmailToo() throws Exception {
        Student s = register("a@college.edu");
        setGoal(s, 2, "20:00", "EMAIL");
        clock.advance(Duration.ofMinutes(31));
        reminders.sendDueReminders();
        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("a@college.edu");
        assertThat(email.sent.get(0).subject()).isEqualTo("Time to study, Aarav");
        assertThat(reminderCount(s)).isEqualTo(1); // and the in-app copy
    }

    @Test
    void aFailingEmailServerDoesNotStopOtherStudentsReminders() throws Exception {
        Student a = register("a@college.edu");
        Student b = register("b@college.edu");
        setGoal(a, 2, "20:00", "EMAIL");
        setGoal(b, 2, "20:00", "EMAIL");
        email.failFor = "a@college.edu";
        clock.advance(Duration.ofMinutes(31));
        assertThat(reminders.sendDueReminders()).isEqualTo(2);
        assertThat(email.sent).extracting(RecordingEmail.Sent::to).containsExactly("b@college.edu");
        assertThat(reminderCount(a)).isEqualTo(1); // the in-app reminder still exists
    }

    @Test
    void aReminderHoursLateIsSkippedAndTomorrowStartsFresh() throws Exception {
        Student s = register("a@college.edu");
        clock.advance(Duration.ofHours(4)); // 23:30, past the 3-hour window
        assertThat(reminders.sendDueReminders()).isZero();
        clock.advance(Duration.ofHours(20).plusMinutes(31)); // next day 20:01
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        clock.advance(Duration.ofHours(24)); // and the day after
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        assertThat(reminderCount(s)).isEqualTo(2);
    }

    @Test
    void eachStudentIsRemindedAtTheirOwnTime() throws Exception {
        Student early = register("a@college.edu");
        Student late = register("b@college.edu");
        setGoal(early, 2, "19:45", "PUSH");
        setGoal(late, 2, "21:00", "PUSH");
        clock.advance(Duration.ofMinutes(20)); // 19:50
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        assertThat(reminderCount(early)).isEqualTo(1);
        assertThat(reminderCount(late)).isZero();
    }

    @Test
    void notificationsArePrivateAndCanBeMarkedRead() throws Exception {
        Student a = register("a@college.edu");
        Student b = register("b@college.edu");
        clock.advance(Duration.ofMinutes(31));
        reminders.sendDueReminders();
        assertThat(notifications(a).get("unread").asInt()).isEqualTo(1);
        mvc.perform(post("/me/notifications/read").header("Authorization", bearer(a))).andExpect(status().isNoContent());
        assertThat(notifications(a).get("unread").asInt()).isZero();
        assertThat(notifications(a).at("/items/0/read").asBoolean()).isTrue();
        assertThat(notifications(b).get("unread").asInt()).isEqualTo(1); // b's are untouched
    }

    @Test
    void endpointsNeedALogin() throws Exception {
        mvc.perform(get("/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/me/notifications/read")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/badges")).andExpect(status().isUnauthorized());
    }

    // ---- badges ----

    @Test
    void meetingTheDailyGoalEarnsFirstGoalOnceAndTellsTheStudent() throws Exception {
        Student s = register("a@college.edu");
        assertThat(badgeList(s)).hasSize(BadgeRules.Badge.values().length);
        assertThat(badgeList(s).findValues("earned").stream().filter(JsonNode::asBoolean)).isEmpty();

        logStudy(s, 120);
        JsonNode list = badgeList(s);
        assertThat(list.findParents("key").stream().filter(n -> n.get("earned").asBoolean()).map(n -> n.get("key").asText()))
                .containsExactly("FIRST_GOAL");
        assertThat(notifications(s).at("/items/0/title").asText()).isEqualTo("Badge earned: First goal met");

        logStudy(s, 30); // more study the same day must not award or notify again
        assertThat(notifications(s).get("items")).hasSize(1);
    }

    @Test
    void passingTheMcqTestAndSolvingAProblemEarnBadges() throws Exception {
        Student s = register("a@college.edu");
        UUID topic1 = jdbc.queryForObject("SELECT t.id FROM topics t JOIN courses c ON c.id = t.course_id WHERE c.slug = 'java' AND t.position = 1", UUID.class);
        jdbc.update("INSERT INTO topic_progress (user_id, topic_id, mcq_passed_at) VALUES (?, ?, ?)", s.id(), topic1, Timestamp.from(START));
        assertThat(badges.award(s.id())).containsExactly(BadgeRules.Badge.MCQ_PASSED);

        UUID problem = jdbc.queryForObject("SELECT id FROM coding_problems WHERE topic_id = ? AND position = 1", UUID.class, topic1);
        jdbc.update("INSERT INTO coding_solved (user_id, problem_id, solved_at) VALUES (?, ?, ?)", s.id(), problem, Timestamp.from(START));
        assertThat(badges.award(s.id())).containsExactly(BadgeRules.Badge.FIRST_SOLVE);

        jdbc.update("UPDATE topic_progress SET coding_passed_at = ? WHERE user_id = ?", Timestamp.from(START), s.id());
        assertThat(badges.award(s.id())).containsExactly(BadgeRules.Badge.TOPIC_CLEARED);
        assertThat(badges.award(s.id())).isEmpty(); // nothing new
    }

    @Test
    void aBrokenStreakDoesNotRemoveABadgeAlreadyEarned() throws Exception {
        Student s = register("a@college.edu");
        logStudy(s, 120);
        clock.advance(Duration.ofDays(5)); // streak is lost
        assertThat(badgeList(s).findParents("key").stream().filter(n -> n.get("earned").asBoolean())).hasSize(1);
    }
}
