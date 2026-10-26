package com.gateready.mcq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gateready.gate.GateRules;
import com.gateready.gate.GateRules.Difficulty;
import com.gateready.gate.GateRules.Kind;
import com.gateready.mcq.McqDrawer.Candidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McqDrawerTest {
    private static List<Candidate> pool(int perCell) {
        List<Candidate> pool = new ArrayList<>();
        for (Kind k : Kind.values()) for (Difficulty d : Difficulty.values())
            for (int i = 0; i < perCell; i++) pool.add(new Candidate(UUID.randomUUID(), k, d));
        return pool;
    }

    private static boolean valid(List<Candidate> drawn) {
        return GateRules.validMcqComposition(drawn.stream().map(c -> new GateRules.Question(c.kind(), c.difficulty())).toList());
    }

    @Test
    void drawsTwentyWithFiveInEachGroupEveryTime() {
        List<Candidate> pool = pool(10);
        for (int seed = 0; seed < 200; seed++) {
            List<Candidate> drawn = McqDrawer.draw(pool, Set.of(), new Random(seed));
            assertThat(drawn).hasSize(20);
            assertThat(valid(drawn)).isTrue();
            assertThat(new HashSet<>(drawn)).hasSize(20); // no question twice
        }
    }

    @Test
    void theoryComesBeforeCode() {
        List<Candidate> drawn = McqDrawer.draw(pool(10), Set.of(), new Random(1));
        assertThat(drawn.subList(0, 10)).allMatch(c -> c.kind() == Kind.THEORY);
        assertThat(drawn.subList(10, 20)).allMatch(c -> c.kind() == Kind.CODE);
    }

    @Test
    void worksWithExactlyFivePerGroup() {
        assertThat(valid(McqDrawer.draw(pool(5), Set.of(), new Random(3)))).isTrue();
    }

    @Test
    void failsWhenAGroupHasTooFewQuestions() {
        List<Candidate> pool = pool(10);
        pool.removeIf(c -> c.kind() == Kind.CODE && c.difficulty() == Difficulty.HARD);
        pool.add(new Candidate(UUID.randomUUID(), Kind.CODE, Difficulty.HARD));
        assertThatThrownBy(() -> McqDrawer.draw(pool, Set.of(), new Random(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRetryAvoidsLastAttemptsQuestionsWhenTheBankIsBigEnough() {
        List<Candidate> pool = pool(10);
        List<Candidate> first = McqDrawer.draw(pool, Set.of(), new Random(5));
        Set<UUID> seen = new HashSet<>();
        first.forEach(c -> seen.add(c.id()));
        for (int seed = 0; seed < 50; seed++) {
            List<Candidate> retry = McqDrawer.draw(pool, seen, new Random(seed));
            assertThat(retry).noneMatch(c -> seen.contains(c.id())); // 5 unseen exist in each group of 10
        }
    }

    @Test
    void seenQuestionsFillInWhenThereAreNotEnoughFreshOnes() {
        List<Candidate> pool = pool(6);
        Set<UUID> seen = new HashSet<>();
        pool.forEach(c -> seen.add(c.id())); // everything seen
        List<Candidate> drawn = McqDrawer.draw(pool, seen, new Random(2));
        assertThat(valid(drawn)).isTrue();
    }

    @Test
    void shuffledOrderIsAPermutationOfZeroToThree() {
        Random r = new Random(9);
        Set<String> distinct = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            String o = McqDrawer.shuffledOrder(r);
            assertThat(o).hasSize(4);
            assertThat(o.chars().sorted().collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append).toString())
                    .isEqualTo("0123");
            distinct.add(o);
        }
        assertThat(distinct.size()).isGreaterThan(10); // it really shuffles
    }
}
