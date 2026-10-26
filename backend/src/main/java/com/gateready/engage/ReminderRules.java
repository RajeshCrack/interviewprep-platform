package com.gateready.engage;

import java.time.LocalTime;

/** When to nudge a student, and what to say. Pure logic, no database. */
public final class ReminderRules {
    /** A reminder is still sent if the server was down at the exact minute, but not hours late. */
    public static final int WINDOW_MINUTES = 180;

    private ReminderRules() {}

    public record Message(String title, String body) {}

    public static boolean due(LocalTime now, LocalTime reminderAt, int minutesToday, int goalMinutes, boolean alreadyRemindedToday) {
        if (alreadyRemindedToday) return false;
        if (goalMinutes > 0 && minutesToday >= goalMinutes) return false;
        int elapsed = now.toSecondOfDay() / 60 - reminderAt.toSecondOfDay() / 60;
        return elapsed >= 0 && elapsed < WINDOW_MINUTES;
    }

    public static Message message(String firstName, int minutesToday, int goalMinutes, int streak) {
        int left = Math.max(0, goalMinutes - minutesToday);
        String streakLine = streak > 0 ? " Your " + streak + "-day streak is on the line." : "";
        if (minutesToday == 0) {
            return new Message("Time to study, " + firstName,
                    "You have not studied yet today. " + left + " minutes meets your goal." + streakLine);
        }
        return new Message("Almost there, " + firstName,
                "You have done " + minutesToday + " minutes today. " + left + " more closes your goal." + streakLine);
    }
}
