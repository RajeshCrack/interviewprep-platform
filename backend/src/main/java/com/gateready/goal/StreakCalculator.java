package com.gateready.goal;

import java.time.LocalDate;

/** Pure streak rules, kept free of the database so they can be tested exhaustively. */
public final class StreakCalculator {
    private StreakCalculator() {}

    /** The streak after the daily goal is met on {@code today}. */
    public static int afterGoalMet(LocalDate lastGoalDate, LocalDate today, int streak) {
        if (lastGoalDate != null && lastGoalDate.equals(today)) return streak;
        if (lastGoalDate != null && lastGoalDate.equals(today.minusDays(1))) return streak + 1;
        return 1;
    }

    /** The streak to show now: it is lost once a whole day passes without meeting the goal. */
    public static int current(LocalDate lastGoalDate, LocalDate today, int streak) {
        if (lastGoalDate == null) return 0;
        if (lastGoalDate.equals(today) || lastGoalDate.equals(today.minusDays(1))) return streak;
        return 0;
    }
}
