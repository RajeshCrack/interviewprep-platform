package com.gateready.coding;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs student code with the JDK installed on this machine. It limits time, memory and output, but it
 * does NOT isolate the program from the machine. Use it for development and tests only. Production
 * uses Judge0 (see Judge0Runner), which runs code in a sandbox.
 */
public class LocalJavaRunner implements CodeRunner {
    private static final int MAX_OUTPUT = 64 * 1024;
    private static final Duration COMPILE_LIMIT = Duration.ofSeconds(30);

    private final Duration timeLimit;
    private final Path root;
    private final Semaphore slots = new Semaphore(2);
    /** The last few compiled sources, so a submission with several tests compiles once. */
    private final Map<String, Path> compiled = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Path> e) {
            if (size() > 8) { delete(e.getValue()); return true; }
            return false;
        }
    };

    public LocalJavaRunner(Duration timeLimit) {
        this.timeLimit = timeLimit;
        try {
            this.root = Files.createTempDirectory("gateready-run");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create a working directory for the code runner", e);
        }
    }

    @Override
    public Outcome run(String sourceCode, String stdin) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.of(Status.UNAVAILABLE, "", "Interrupted");
        }
        try {
            Path dir;
            synchronized (compiled) {
                dir = compiled.get(sourceCode);
                if (dir == null) {
                    Compiled c = compile(sourceCode);
                    if (c.failure() != null) return c.failure();
                    dir = c.dir();
                    compiled.put(sourceCode, dir);
                }
            }
            return execute(dir, stdin);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Outcome.of(Status.UNAVAILABLE, "", "The runner failed: " + e.getMessage());
        } finally {
            slots.release();
        }
    }

    private record Compiled(Path dir, Outcome failure) {}

    private Compiled compile(String source) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory(root, "c");
        Files.writeString(dir.resolve("Main.java"), source, StandardCharsets.UTF_8);
        Path log = dir.resolve("javac.log");
        Process p = base(new ProcessBuilder("javac", "-proc:none", "-encoding", "UTF-8", "Main.java").directory(dir.toFile()))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!p.waitFor(COMPILE_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
            p.destroyForcibly();
            delete(dir);
            return new Compiled(null, Outcome.of(Status.UNAVAILABLE, "", "The compiler timed out."));
        }
        if (p.exitValue() != 0) {
            String message = clean(read(log)).replace(dir.toString() + "/", "");
            delete(dir);
            return new Compiled(null, Outcome.of(Status.COMPILE_ERROR, "", message));
        }
        return new Compiled(dir, null);
    }

    private Outcome execute(Path dir, String stdin) throws IOException, InterruptedException {
        Path in = Files.createTempFile(dir, "in", ".txt");
        Path out = Files.createTempFile(dir, "out", ".txt");
        Path err = Files.createTempFile(dir, "err", ".txt");
        Files.writeString(in, stdin == null ? "" : stdin, StandardCharsets.UTF_8);
        Process p = base(new ProcessBuilder("java", "-Xmx128m", "-Xss8m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-cp", dir.toString(), "Main"))
                .redirectInput(in.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        boolean finished = p.waitFor(timeLimit.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            p.waitFor(2, TimeUnit.SECONDS);
            return Outcome.of(Status.TIME_LIMIT, clean(read(out)), "");
        }
        String stdout = read(out);
        String stderr = clean(read(err));
        Files.deleteIfExists(in); Files.deleteIfExists(out); Files.deleteIfExists(err);
        return p.exitValue() == 0 ? Outcome.of(Status.OK, stdout, stderr) : Outcome.of(Status.RUNTIME_ERROR, stdout, stderr);
    }

    private static ProcessBuilder base(ProcessBuilder pb) {
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("_JAVA_OPTIONS");
        pb.environment().remove("JDK_JAVA_OPTIONS");
        return pb;
    }

    private static String read(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return new String(in.readNBytes(MAX_OUTPUT), StandardCharsets.UTF_8);
        }
    }

    private static String clean(String s) {
        return s.lines().filter(l -> !l.startsWith("Picked up ")).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static void delete(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) { /* best effort */ }
    }

    @PreDestroy
    void cleanUp() {
        delete(root);
    }
}
