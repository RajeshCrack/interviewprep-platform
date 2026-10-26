package com.gateready.gate;

import static com.gateready.gate.GateRules.Difficulty.EASY;
import static com.gateready.gate.GateRules.Difficulty.HARD;
import static com.gateready.gate.GateRules.Kind.CODE;
import static com.gateready.gate.GateRules.Kind.THEORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gateready.gate.GateRules.Question;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GateRulesTest {

    @Test
    void passMarkIs14OutOf20() {
        assertThat(GateRules.passMark()).isEqualTo(14);
    }

    @ParameterizedTest(name = "{0} of 20 -> passed={1}")
    @CsvSource({"0,false", "1,false", "13,false", "14,true", "15,true", "19,true", "20,true"})
    void mcqBoundary(int correct, boolean expected) {
        assertThat(GateRules.mcqPassed(correct)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"-1", "21", "100"})
    void mcqRejectsImpossibleScores(int correct) {
        assertThatThrownBy(() -> GateRules.mcqPassed(correct)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Question> cell(GateRules.Kind k, GateRules.Difficulty d, int n) {
        List<Question> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Question(k, d));
        return list;
    }

    private static List<Question> valid() {
        List<Question> all = new ArrayList<>();
        all.addAll(cell(THEORY, EASY, 5));
        all.addAll(cell(THEORY, HARD, 5));
        all.addAll(cell(CODE, EASY, 5));
        all.addAll(cell(CODE, HARD, 5));
        return all;
    }

    @Test
    void validTestHas5QuestionsInEachOfTheFourCells() {
        assertThat(GateRules.validMcqComposition(valid())).isTrue();
    }

    @Test
    void wrongSizeIsRejected() {
        List<Question> short19 = valid().subList(0, 19);
        assertThat(GateRules.validMcqComposition(short19)).isFalse();
        List<Question> long21 = new ArrayList<>(valid());
        long21.add(new Question(THEORY, EASY));
        assertThat(GateRules.validMcqComposition(long21)).isFalse();
    }

    @Test
    void unbalancedCellsAreRejectedEvenWhenTheTotalIs20() {
        List<Question> all = new ArrayList<>();
        all.addAll(cell(THEORY, EASY, 6));
        all.addAll(cell(THEORY, HARD, 4));
        all.addAll(cell(CODE, EASY, 5));
        all.addAll(cell(CODE, HARD, 5));
        assertThat(GateRules.validMcqComposition(all)).isFalse();
        List<Question> noCode = new ArrayList<>();
        noCode.addAll(cell(THEORY, EASY, 10));
        noCode.addAll(cell(THEORY, HARD, 10));
        assertThat(GateRules.validMcqComposition(noCode)).isFalse();
    }

    @ParameterizedTest(name = "easy {0}, total {1} -> passed={2}")
    @CsvSource({"2,5,true", "2,3,true", "2,4,true", "2,2,false", "1,4,false", "0,3,false", "1,3,false", "0,0,false"})
    void codingRound(int easy, int total, boolean expected) {
        assertThat(GateRules.codingPassed(easy, total)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"3,5", "2,6", "-1,2", "2,1"})
    void codingRejectsImpossibleResults(int easy, int total) {
        assertThatThrownBy(() -> GateRules.codingPassed(easy, total)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topicNeedsBothRounds() {
        assertThat(GateRules.topicPassed(true, true)).isTrue();
        assertThat(GateRules.topicPassed(true, false)).isFalse();
        assertThat(GateRules.topicPassed(false, true)).isFalse();
        assertThat(GateRules.topicPassed(false, false)).isFalse();
    }

    @Test
    void codingRoundOpensOnlyAfterMcqPass() {
        assertThat(GateRules.codingRoundOpen(false)).isFalse();
        assertThat(GateRules.codingRoundOpen(true)).isTrue();
    }

    @Test
    void firstTopicIsAlwaysOpenAndLaterTopicsNeedThePreviousOne() {
        assertThat(GateRules.topicUnlocked(1, false)).isTrue();
        assertThat(GateRules.topicUnlocked(2, false)).isFalse();
        assertThat(GateRules.topicUnlocked(2, true)).isTrue();
    }
}
