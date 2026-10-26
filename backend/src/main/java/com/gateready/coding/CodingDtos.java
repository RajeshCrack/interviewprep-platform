package com.gateready.coding;

import java.util.List;
import java.util.UUID;

public final class CodingDtos {
    private CodingDtos() {}

    public record Sample(String input, String expected) {}

    public record ProblemView(UUID id, int position, String difficulty, String title, String statement, String starterCode,
                              List<Sample> samples, boolean solved, String lastCode) {}

    public record RoundView(UUID topicId, boolean passed, int solvedCount, int totalProblems, int solvedEasy, int totalEasy,
                            int neededTotal, List<ProblemView> problems) {}

    public record SubmitRequest(String code, String mode) {}

    /** input, expected and actual are filled only for visible tests; a hidden test shows pass or fail and nothing else. */
    public record TestResult(int position, boolean visible, boolean passed, String status, String input, String expected,
                             String actual, String error) {}

    public record SubmissionResult(UUID id, String mode, String status, int passedTests, int totalTests, String message,
                                   List<TestResult> tests, boolean solved, boolean roundPassed, int xpAwarded) {}
}
