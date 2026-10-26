package com.gateready.engage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks for due reminders every minute. Turn off with app.reminders.enabled=false. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
class ReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private final ReminderService reminders;

    ReminderScheduler(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    void tick() {
        try {
            int sent = reminders.sendDueReminders();
            if (sent > 0) log.info("Sent {} study reminders", sent);
        } catch (RuntimeException e) {
            log.error("Reminder run failed", e); // never let one bad run stop the schedule
        }
    }
}
