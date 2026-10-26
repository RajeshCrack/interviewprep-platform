package com.gateready.mcq;

import com.gateready.gate.GateRules;
import com.gateready.gate.GateRules.Difficulty;
import com.gateready.gate.GateRules.Kind;
import java.util.ArrayList;
import java.util.List;

/** Marks a finished test. Pure logic: the answer key never leaves the server. */
public final class McqGrader {
    /** selected is the option the student saw (0 to 3), or null when left blank. */
    public record Answer(Kind kind, Difficulty difficulty, int correctOption, String optionOrder, Integer selected) {
        public boolean correct() {
            return selected != null && selected >= 0 && selected < 4
                    && optionOrder.charAt(selected) - '0' == correctOption;
        }

        /** The position of the right answer as the student saw it. */
        public int correctDisplay() {
            return optionOrder.indexOf((char) ('0' + correctOption));
        }
    }

    public record GroupScore(Kind kind, Difficulty difficulty, int correct, int total) {}

    public record Result(int correct, boolean passed, List<GroupScore> groups) {}

    private McqGrader() {}

    public static Result grade(List<Answer> answers) {
        if (answers.size() != GateRules.MCQ_QUESTIONS) {
            throw new IllegalArgumentException("A test has exactly " + GateRules.MCQ_QUESTIONS + " questions");
        }
        int correct = 0;
        List<GroupScore> groups = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            for (Difficulty difficulty : Difficulty.values()) {
                int total = 0;
                int right = 0;
                for (Answer a : answers) {
                    if (a.kind() != kind || a.difficulty() != difficulty) continue;
                    total++;
                    if (a.correct()) right++;
                }
                correct += right;
                groups.add(new GroupScore(kind, difficulty, right, total));
            }
        }
        return new Result(correct, GateRules.mcqPassed(correct), groups);
    }
}
