package com.gateready.course;

import java.util.List;
import java.util.UUID;

public final class CourseDtos {
    private CourseDtos() {}

    public enum TopicStatus { PASSED, CURRENT, LOCKED }

    public record CourseSummary(String slug, String title, String description, String icon, boolean published, int topicCount) {}

    public record TopicRow(UUID id, int position, String title, int estMinutes, boolean hasContent,
                           boolean mcqPassed, boolean codingPassed) {}

    public record RoadmapTopic(UUID id, int position, String title, int estMinutes, TopicStatus status,
                               boolean mcqPassed, boolean codingPassed, boolean hasContent) {}

    public record Roadmap(CourseSummary course, int passedCount, List<RoadmapTopic> topics) {}

    public record TopicDetail(UUID id, String courseSlug, int position, int totalTopics, String title, int estMinutes,
                              boolean hasContent, String what, String why, String exampleCode, List<String> hints,
                              String videoQuery, boolean mcqPassed, boolean codingPassed, UUID nextTopicId) {}
}
