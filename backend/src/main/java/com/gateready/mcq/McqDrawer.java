package com.gateready.mcq;

import com.gateready.gate.GateRules;
import com.gateready.gate.GateRules.Difficulty;
import com.gateready.gate.GateRules.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Picks the 20 questions of one test: 5 from each of the four kind-and-difficulty groups.
 * Questions the student saw in the previous attempt are used last, so a retry feels new.
 * Pure logic, no database.
 */
public final class McqDrawer {
    public record Candidate(UUID id, Kind kind, Difficulty difficulty) {}

    private McqDrawer() {}

    /** Theory questions come first, then code questions; the order inside each half is shuffled. */
    public static List<Candidate> draw(List<Candidate> pool, Set<UUID> recentlySeen, Random random) {
        List<Candidate> theory = new ArrayList<>();
        List<Candidate> code = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            for (Difficulty difficulty : Difficulty.values()) {
                List<Candidate> picked = pickCell(pool, kind, difficulty, recentlySeen, random);
                (kind == Kind.THEORY ? theory : code).addAll(picked);
            }
        }
        Collections.shuffle(theory, random);
        Collections.shuffle(code, random);
        List<Candidate> all = new ArrayList<>(theory);
        all.addAll(code);
        return all;
    }

    private static List<Candidate> pickCell(List<Candidate> pool, Kind kind, Difficulty difficulty,
                                            Set<UUID> recentlySeen, Random random) {
        List<Candidate> fresh = new ArrayList<>();
        List<Candidate> seen = new ArrayList<>();
        for (Candidate c : pool) {
            if (c.kind() != kind || c.difficulty() != difficulty) continue;
            (recentlySeen.contains(c.id()) ? seen : fresh).add(c);
        }
        if (fresh.size() + seen.size() < GateRules.CELL_SIZE) {
            throw new IllegalStateException("Question bank has fewer than " + GateRules.CELL_SIZE
                    + " " + kind + " " + difficulty + " questions");
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(seen, random);
        List<Candidate> picked = new ArrayList<>(fresh);
        picked.addAll(seen);
        return new ArrayList<>(picked.subList(0, GateRules.CELL_SIZE));
    }

    /** A random permutation of "0123". Displayed option k shows stored option order.charAt(k). */
    public static String shuffledOrder(Random random) {
        List<Integer> digits = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(digits, random);
        StringBuilder sb = new StringBuilder();
        digits.forEach(sb::append);
        return sb.toString();
    }
}
