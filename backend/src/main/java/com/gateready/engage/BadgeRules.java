package com.gateready.engage;

import java.util.EnumSet;
import java.util.Set;

/** Which badges a student has earned, from a snapshot of their progress. Pure logic. */
public final class BadgeRules {
    public enum Badge {
        FIRST_GOAL("First goal met", "Meet your daily goal once."),
        STREAK_3("3-day streak", "Meet your goal 3 days in a row."),
        STREAK_7("7-day streak", "Meet your goal 7 days in a row."),
        STREAK_30("30-day streak", "Meet your goal 30 days in a row."),
        MCQ_PASSED("Test taker", "Pass your first MCQ test."),
        FIRST_SOLVE("Problem solver", "Solve your first coding problem."),
        TOPIC_CLEARED("Topic cleared", "Pass both rounds of a topic."),
        HALF_WAY("Halfway there", "Clear 5 topics."),
        COURSE_DONE("Course complete", "Clear all 10 topics of a course.");

        public final String title;
        public final String description;

        Badge(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    /** streak is the current streak. */
    public record Snapshot(int streak, int mcqPassedTopics, int solvedProblems, int clearedTopics) {}

    private BadgeRules() {}

    public static Set<Badge> earned(Snapshot s) {
        Set<Badge> out = EnumSet.noneOf(Badge.class);
        if (s.streak() >= 1) out.add(Badge.FIRST_GOAL);
        if (s.streak() >= 3) out.add(Badge.STREAK_3);
        if (s.streak() >= 7) out.add(Badge.STREAK_7);
        if (s.streak() >= 30) out.add(Badge.STREAK_30);
        if (s.mcqPassedTopics() >= 1) out.add(Badge.MCQ_PASSED);
        if (s.solvedProblems() >= 1) out.add(Badge.FIRST_SOLVE);
        if (s.clearedTopics() >= 1) out.add(Badge.TOPIC_CLEARED);
        if (s.clearedTopics() >= 5) out.add(Badge.HALF_WAY);
        if (s.clearedTopics() >= 10) out.add(Badge.COURSE_DONE);
        return out;
    }
}
