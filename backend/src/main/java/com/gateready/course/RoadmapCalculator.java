package com.gateready.course;

import com.gateready.course.CourseDtos.RoadmapTopic;
import com.gateready.course.CourseDtos.TopicRow;
import com.gateready.course.CourseDtos.TopicStatus;
import com.gateready.gate.GateRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns a course's topics and one student's results into PASSED, CURRENT and LOCKED. Pure, no database. */
public final class RoadmapCalculator {
    private RoadmapCalculator() {}

    public static List<RoadmapTopic> compute(List<TopicRow> rows) {
        List<TopicRow> ordered = rows.stream().sorted(Comparator.comparingInt(TopicRow::position)).toList();
        List<RoadmapTopic> out = new ArrayList<>();
        boolean previousPassed = false;
        for (int i = 0; i < ordered.size(); i++) {
            TopicRow r = ordered.get(i);
            boolean passed = GateRules.topicPassed(r.mcqPassed(), r.codingPassed());
            // Topics are unlocked by position in the list, so a gap in numbering can never skip a gate.
            boolean unlocked = GateRules.topicUnlocked(i + 1, previousPassed);
            TopicStatus status = !unlocked ? TopicStatus.LOCKED : passed ? TopicStatus.PASSED : TopicStatus.CURRENT;
            out.add(new RoadmapTopic(r.id(), r.position(), r.title(), r.estMinutes(), status,
                    unlocked && r.mcqPassed(), unlocked && r.codingPassed(), r.hasContent()));
            previousPassed = unlocked && passed;
        }
        return out;
    }
}
