package com.gateready.mcq;

import com.gateready.gate.GateRules.Difficulty;
import com.gateready.gate.GateRules.Kind;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class McqDtos {
    private McqDtos() {}

    /** A question as the student sees it. There is deliberately no answer or explanation here. */
    public record QuestionView(int position, Kind kind, String prompt, String codeSnippet, List<String> options,
                               Integer selected) {}

    public record AttemptView(UUID id, UUID topicId, Instant startedAt, Instant expiresAt, int total,
                              List<QuestionView> questions) {}

    public record SubmitRequest(@NotNull List<Integer> answers) {}

    public record AnswerRequest(Integer selected) {}

    public record GroupView(Kind kind, Difficulty difficulty, int correct, int total) {}

    public record ReviewItem(int position, Kind kind, Difficulty difficulty, String prompt, String codeSnippet,
                             List<String> options, Integer selected, int correct, boolean isCorrect, String explanation) {}

    public record ResultView(UUID attemptId, UUID topicId, int score, int total, int passMark, boolean passed,
                             Instant submittedAt, int xpAwarded, List<GroupView> groups, List<ReviewItem> review) {}
}
