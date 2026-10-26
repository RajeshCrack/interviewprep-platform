package com.gateready.coding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class LocalJavaRunnerTest {
    private static final LocalJavaRunner runner = new LocalJavaRunner(Duration.ofSeconds(2));

    @AfterAll static void done() { runner.cleanUp(); }

    private static String program(String body) {
        return "import java.util.*;\npublic class Main { public static void main(String[] a) throws Exception { " + body + " } }";
    }

    @Test
    void runsAProgramAndFeedsItStdin() {
        var o = runner.run(program("Scanner in = new Scanner(System.in); System.out.println(in.nextInt() * 2);"), "21\n");
        assertThat(o.status()).isEqualTo(CodeRunner.Status.OK);
        assertThat(o.stdout().trim()).isEqualTo("42");
    }

    @Test
    void reportsCompileErrorsWithTheMessage() {
        var o = runner.run(program("int x = \"text\";"), "");
        assertThat(o.status()).isEqualTo(CodeRunner.Status.COMPILE_ERROR);
        assertThat(o.stderr()).contains("incompatible types").doesNotContain("Picked up");
    }

    @Test
    void usesTheSameJavaLevelAsJudge0() {
        var o = runner.run("public class Main { record P(int x) {} public static void main(String[] a) { System.out.println(new P(1)); } }", "");
        assertThat(o.status()).isEqualTo(CodeRunner.Status.COMPILE_ERROR); // records need Java 14 or later
    }

    @Test
    void reportsRuntimeExceptions() {
        var o = runner.run(program("int[] x = new int[1]; System.out.println(x[2]);"), "");
        assertThat(o.status()).isEqualTo(CodeRunner.Status.RUNTIME_ERROR);
        assertThat(o.stderr()).contains("ArrayIndexOutOfBoundsException");
    }

    @Test
    void killsAnEndlessLoop() {
        long start = System.currentTimeMillis();
        var o = runner.run(program("while (true) { }"), "");
        assertThat(o.status()).isEqualTo(CodeRunner.Status.TIME_LIMIT);
        assertThat(System.currentTimeMillis() - start).isLessThan(8000);
    }

    @Test
    void aProgramThatNeverReadsItsInputStillFinishes() {
        var o = runner.run(program("System.out.println(\"hi\");"), "x".repeat(100_000));
        assertThat(o.status()).isEqualTo(CodeRunner.Status.OK);
    }

    @Test
    void theSameSourceRunsManyTimesWithDifferentInput() {
        String src = program("System.out.println(new Scanner(System.in).nextInt() + 1);");
        for (int i = 0; i < 5; i++) assertThat(runner.run(src, i + "\n").stdout().trim()).isEqualTo(String.valueOf(i + 1));
    }

    @Test
    void theDisabledRunnerNeverRunsCode() {
        assertThat(new DisabledRunner().run(program("System.out.println(1);"), "").status()).isEqualTo(CodeRunner.Status.UNAVAILABLE);
    }
}
