package com.gateready.mcq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gateready.gate.GateRules.Difficulty;
import com.gateready.gate.GateRules.Kind;
import com.gateready.mcq.McqGrader.Answer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class McqGraderTest {
    /** Question i is in group i/5 (T-easy, T-hard, C-easy, C-hard). Stored answer is option 1; order "0123". */
    private static List<Answer> test(int correctCount) {
        List<Answer> list = new ArrayList<>();
        Kind[] kinds = {Kind.THEORY, Kind.THEORY, Kind.CODE, Kind.CODE};
        Difficulty[] ds = {Difficulty.EASY, Difficulty.HARD, Difficulty.EASY, Difficulty.HARD};
        for (int i = 0; i < 20; i++) {
            list.add(new Answer(kinds[i / 5], ds[i / 5], 1, "0123", i < correctCount ? 1 : 2));
        }
        return list;
    }

    @Test
    void thirteenFailsAndFourteenPasses() {
        assertThat(McqGrader.grade(test(13)).passed()).isFalse();
        assertThat(McqGrader.grade(test(14)).passed()).isTrue();
        assertThat(McqGrader.grade(test(14)).correct()).isEqualTo(14);
    }

    @Test
    void extremesScoreZeroAndTwenty() {
        assertThat(McqGrader.grade(test(0)).correct()).isZero();
        assertThat(McqGrader.grade(test(20)).correct()).isEqualTo(20);
        assertThat(McqGrader.grade(test(20)).passed()).isTrue();
    }

    @Test
    void blankAnswersCountAsWrong() {
        List<Answer> blank = new ArrayList<>();
        for (Answer a : test(20)) blank.add(new Answer(a.kind(), a.difficulty(), a.correctOption(), a.optionOrder(), null));
        assertThat(McqGrader.grade(blank).correct()).isZero();
    }

    @Test
    void shuffledOptionsAreMappedBackToTheStoredAnswer() {
        // Stored correct option is 1. Order "2301" shows stored options 2,3,0,1, so the student sees the right one at index 3.
        Answer a = new Answer(Kind.CODE, Difficulty.EASY, 1, "2301", 3);
        assertThat(a.correct()).isTrue();
        assertThat(a.correctDisplay()).isEqualTo(3);
        assertThat(new Answer(Kind.CODE, Difficulty.EASY, 1, "2301", 1).correct()).isFalse();
    }

    @Test
    void groupScoresAddUpToTheTotal() {
        McqGrader.Result r = McqGrader.grade(test(12)); // first 12: all of group 0 and 1, plus 2 of group 2
        assertThat(r.groups()).hasSize(4);
        assertThat(r.groups().stream().mapToInt(McqGrader.GroupScore::total)).containsOnly(5);
        assertThat(r.groups().stream().mapToInt(McqGrader.GroupScore::correct)).containsExactly(5, 5, 2, 0);
        assertThat(r.groups().stream().mapToInt(McqGrader.GroupScore::correct).sum()).isEqualTo(r.correct());
    }

    @Test
    void refusesATestThatIsNotTwentyQuestions() {
        assertThatThrownBy(() -> McqGrader.grade(test(20).subList(0, 19))).isInstanceOf(IllegalArgumentException.class);
    }
}
