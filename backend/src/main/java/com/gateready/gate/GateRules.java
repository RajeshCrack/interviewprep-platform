package com.gateready.gate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The rules that decide whether a student passes a topic. Pure functions, no database,
 * so every boundary can be tested. Rules come from the design document, sections 1 and 7.
 */
public final class GateRules {
    public static final int MCQ_QUESTIONS = 20;
    public static final int MCQ_PASS_PERCENT = 70;
    public static final int CELL_SIZE = 5;
    public static final int CODING_PROBLEMS = 5;
    /** Assumption awaiting confirmation: at least 3 of 5 solved, including both easy problems. */
    public static final int CODING_MIN_SOLVED = 3;
    public static final int CODING_EASY_COUNT = 2;

    public enum Kind { THEORY, CODE }
    public enum Difficulty { EASY, HARD }
    public record Question(Kind kind, Difficulty difficulty) {}

    private GateRules() {}

    public static int passMark() {
        return MCQ_QUESTIONS * MCQ_PASS_PERCENT / 100; // 14
    }

    public static boolean mcqPassed(int correct) {
        if (correct < 0 || correct > MCQ_QUESTIONS) {
            throw new IllegalArgumentException("correct must be between 0 and " + MCQ_QUESTIONS);
        }
        return correct >= passMark();
    }

    /** A drawn test is valid only with exactly 5 questions in each of the four kind-and-difficulty cells. */
    public static boolean validMcqComposition(List<Question> drawn) {
        if (drawn.size() != MCQ_QUESTIONS) return false;
        Map<Kind, Map<Difficulty, Integer>> counts = new EnumMap<>(Kind.class);
        for (Question q : drawn) {
            counts.computeIfAbsent(q.kind(), k -> new EnumMap<>(Difficulty.class)).merge(q.difficulty(), 1, Integer::sum);
        }
        for (Kind k : Kind.values()) {
            for (Difficulty d : Difficulty.values()) {
                if (counts.getOrDefault(k, Map.of()).getOrDefault(d, 0) != CELL_SIZE) return false;
            }
        }
        return true;
    }

    public static boolean codingPassed(int solvedEasy, int solvedTotal) {
        if (solvedEasy < 0 || solvedEasy > CODING_EASY_COUNT || solvedTotal < solvedEasy || solvedTotal > CODING_PROBLEMS) {
            throw new IllegalArgumentException("impossible coding result");
        }
        return solvedTotal >= CODING_MIN_SOLVED && solvedEasy == CODING_EASY_COUNT;
    }

    public static boolean topicPassed(boolean mcqPassed, boolean codingPassed) {
        return mcqPassed && codingPassed;
    }

    /** The coding round opens only after the MCQ test is passed. */
    public static boolean codingRoundOpen(boolean mcqPassed) {
        return mcqPassed;
    }

    /** Topic n (1-based) is open when it is the first, or the previous topic is passed. */
    public static boolean topicUnlocked(int topicNumber, boolean previousTopicPassed) {
        return topicNumber == 1 || previousTopicPassed;
    }
}
