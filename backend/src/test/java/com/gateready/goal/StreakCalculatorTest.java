package com.gateready.goal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void firstEverGoalStartsAStreakOfOne() {
        assertThat(StreakCalculator.afterGoalMet(null, TODAY, 0)).isEqualTo(1);
    }

    @Test
    void consecutiveDayExtendsTheStreak() {
        assertThat(StreakCalculator.afterGoalMet(TODAY.minusDays(1), TODAY, 4)).isEqualTo(5);
    }

    @Test
    void meetingTheGoalTwiceInADayDoesNotCountTwice() {
        assertThat(StreakCalculator.afterGoalMet(TODAY, TODAY, 4)).isEqualTo(4);
    }

    @Test
    void aMissedDayRestartsAtOne() {
        assertThat(StreakCalculator.afterGoalMet(TODAY.minusDays(2), TODAY, 9)).isEqualTo(1);
    }

    @Test
    void displayedStreakSurvivesTodayAndYesterdayOnly() {
        assertThat(StreakCalculator.current(null, TODAY, 0)).isZero();
        assertThat(StreakCalculator.current(TODAY, TODAY, 3)).isEqualTo(3);
        assertThat(StreakCalculator.current(TODAY.minusDays(1), TODAY, 3)).isEqualTo(3);
        assertThat(StreakCalculator.current(TODAY.minusDays(2), TODAY, 3)).isZero();
    }

    @Test
    void worksAcrossMonthAndYearBoundaries() {
        assertThat(StreakCalculator.afterGoalMet(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), 6)).isEqualTo(7);
    }
}
