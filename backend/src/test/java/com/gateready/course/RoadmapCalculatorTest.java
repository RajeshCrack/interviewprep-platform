package com.gateready.course;

import static com.gateready.course.CourseDtos.TopicStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.gateready.course.CourseDtos.RoadmapTopic;
import com.gateready.course.CourseDtos.TopicRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoadmapCalculatorTest {

    private static TopicRow row(int pos, boolean mcq, boolean coding) {
        return new TopicRow(UUID.randomUUID(), pos, "Topic " + pos, 30, true, mcq, coding);
    }

    private static List<RoadmapTopic> run(boolean[][] results) {
        List<TopicRow> rows = new ArrayList<>();
        for (int i = 0; i < results.length; i++) rows.add(row(i + 1, results[i][0], results[i][1]));
        return RoadmapCalculator.compute(rows);
    }

    @Test
    void aNewStudentHasTopicOneOpenAndEverythingElseLocked() {
        var r = run(new boolean[][] {{false, false}, {false, false}, {false, false}});
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(CURRENT, LOCKED, LOCKED);
    }

    @Test
    void passingOnlyTheMcqDoesNotUnlockTheNextTopic() {
        var r = run(new boolean[][] {{true, false}, {false, false}});
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(CURRENT, LOCKED);
        assertThat(r.get(0).mcqPassed()).isTrue();
        assertThat(r.get(0).codingPassed()).isFalse();
    }

    @Test
    void passingOnlyTheCodingRoundDoesNotUnlockTheNextTopicEither() {
        var r = run(new boolean[][] {{false, true}, {false, false}});
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(CURRENT, LOCKED);
    }

    @Test
    void passingBothRoundsUnlocksExactlyTheNextTopic() {
        var r = run(new boolean[][] {{true, true}, {false, false}, {false, false}});
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(PASSED, CURRENT, LOCKED);
    }

    @Test
    void aPassedTopicAfterAnUnpassedOneStaysLocked() {
        // Bad data (a later topic marked passed while an earlier one is not) must never open the later topic.
        var r = run(new boolean[][] {{true, false}, {true, true}, {false, false}});
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(CURRENT, LOCKED, LOCKED);
        assertThat(r.get(1).mcqPassed()).isFalse();
    }

    @Test
    void finishingEveryTopicLeavesNoCurrentTopic() {
        var r = run(new boolean[][] {{true, true}, {true, true}});
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(PASSED, PASSED);
    }

    @Test
    void topicsAreOrderedByPositionEvenIfGivenOutOfOrder() {
        List<TopicRow> rows = new ArrayList<>(List.of(row(3, false, false), row(1, true, true), row(2, false, false)));
        var r = RoadmapCalculator.compute(rows);
        assertThat(r).extracting(RoadmapTopic::position).containsExactly(1, 2, 3);
        assertThat(r).extracting(RoadmapTopic::status).containsExactly(PASSED, CURRENT, LOCKED);
    }

    @Test
    void anEmptyCourseHasAnEmptyRoadmap() {
        assertThat(RoadmapCalculator.compute(List.of())).isEmpty();
    }
}
