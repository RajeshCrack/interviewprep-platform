package com.gateready.engage;

import static com.gateready.engage.BadgeRules.Badge.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.gateready.engage.BadgeRules.Snapshot;
import org.junit.jupiter.api.Test;

class BadgeRulesTest {
    @Test void aNewStudentHasNoBadges() { assertThat(BadgeRules.earned(new Snapshot(0, 0, 0, 0))).isEmpty(); }

    @Test void streakBadgesStackAtTheirThresholds() {
        assertThat(BadgeRules.earned(new Snapshot(1, 0, 0, 0))).containsExactlyInAnyOrder(FIRST_GOAL);
        assertThat(BadgeRules.earned(new Snapshot(2, 0, 0, 0))).containsExactlyInAnyOrder(FIRST_GOAL);
        assertThat(BadgeRules.earned(new Snapshot(3, 0, 0, 0))).containsExactlyInAnyOrder(FIRST_GOAL, STREAK_3);
        assertThat(BadgeRules.earned(new Snapshot(7, 0, 0, 0))).containsExactlyInAnyOrder(FIRST_GOAL, STREAK_3, STREAK_7);
        assertThat(BadgeRules.earned(new Snapshot(30, 0, 0, 0))).contains(STREAK_30);
        assertThat(BadgeRules.earned(new Snapshot(29, 0, 0, 0))).doesNotContain(STREAK_30);
    }

    @Test void learningBadges() {
        assertThat(BadgeRules.earned(new Snapshot(0, 1, 0, 0))).containsExactly(MCQ_PASSED);
        assertThat(BadgeRules.earned(new Snapshot(0, 0, 1, 0))).containsExactly(FIRST_SOLVE);
        assertThat(BadgeRules.earned(new Snapshot(0, 1, 3, 1))).containsExactlyInAnyOrder(MCQ_PASSED, FIRST_SOLVE, TOPIC_CLEARED);
    }

    @Test void courseProgressBadges() {
        assertThat(BadgeRules.earned(new Snapshot(0, 4, 0, 4))).doesNotContain(HALF_WAY);
        assertThat(BadgeRules.earned(new Snapshot(0, 5, 0, 5))).contains(HALF_WAY).doesNotContain(COURSE_DONE);
        assertThat(BadgeRules.earned(new Snapshot(0, 10, 0, 10))).contains(HALF_WAY, COURSE_DONE);
    }

    @Test void everyBadgeHasATitleAndDescription() {
        for (var b : BadgeRules.Badge.values()) { assertThat(b.title).isNotBlank(); assertThat(b.description).isNotBlank(); }
    }
}
