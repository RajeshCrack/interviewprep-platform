package com.gateready.goal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GoalDto(
        @DecimalMin(value = "0.5", message = "Study at least 0.5 hours a day.")
        @DecimalMax(value = "4", message = "The daily goal cannot be more than 4 hours.")
        double hoursPerDay,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Reminder time must look like 20:00.")
        String reminderTime,
        @NotNull(message = "Pick a reminder channel.") Channel channel) {

    public static GoalDto of(Goal g) {
        return new GoalDto(g.getHoursPerDay(), g.getReminderTime(), g.getChannel());
    }
}
