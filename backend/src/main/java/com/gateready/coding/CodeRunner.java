package com.gateready.coding;

/** Runs one Java program (a class named Main) against one stdin and reports what happened. */
public interface CodeRunner {
    enum Status { OK, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT, UNAVAILABLE }

    record Outcome(Status status, String stdout, String stderr) {
        static Outcome of(Status s, String stdout, String stderr) { return new Outcome(s, stdout, stderr); }
    }

    Outcome run(String sourceCode, String stdin);
}
