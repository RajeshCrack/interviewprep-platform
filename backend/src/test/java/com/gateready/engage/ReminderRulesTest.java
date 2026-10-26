package com.gateready.engage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ReminderRulesTest {
    private static final LocalTime EIGHT_PM = LocalTime.of(20, 0);

    @Test void notDueBeforeTheChosenTime() { assertThat(ReminderRules.due(LocalTime.of(19, 59), EIGHT_PM, 0, 120, false)).isFalse(); }

    @Test void dueAtTheChosenMinute() {
        assertThat(ReminderRules.due(LocalTime.of(20, 0, 30), EIGHT_PM, 0, 120, false)).isTrue();
    }

    @Test void stillDueIfTheServerWasLateButNotHoursLate() {
        assertThat(ReminderRules.due(LocalTime.of(22, 59), EIGHT_PM, 0, 120, false)).isTrue();
        assertThat(ReminderRules.due(LocalTime.of(23, 0), EIGHT_PM, 0, 120, false)).isFalse();
    }

    @Test void notDueOnceTheGoalIsMet() {
        assertThat(ReminderRules.due(EIGHT_PM, EIGHT_PM, 120, 120, false)).isFalse();
        assertThat(ReminderRules.due(EIGHT_PM, EIGHT_PM, 119, 120, false)).isTrue();
    }

    @Test void neverTwiceInADay() { assertThat(ReminderRules.due(EIGHT_PM, EIGHT_PM, 0, 120, true)).isFalse(); }

    @Test void aLateEveningReminderDoesNotWrapPastMidnight() {
        LocalTime late = LocalTime.of(23, 30);
        assertThat(ReminderRules.due(LocalTime.of(23, 59), late, 0, 60, false)).isTrue();
        assertThat(ReminderRules.due(LocalTime.of(0, 10), late, 0, 60, false)).isFalse();
    }

    @Test void messageForAStudentWhoHasNotStarted() {
        var m = ReminderRules.message("Aarav", 0, 120, 0);
        assertThat(m.title()).isEqualTo("Time to study, Aarav");
        assertThat(m.body()).contains("120 minutes").doesNotContain("streak");
    }

    @Test void messageMentionsTheStreakAndWhatIsLeft() {
        var m = ReminderRules.message("Aarav", 45, 120, 6);
        assertThat(m.title()).isEqualTo("Almost there, Aarav");
        assertThat(m.body()).contains("45 minutes").contains("75 more").contains("6-day streak");
    }
}
