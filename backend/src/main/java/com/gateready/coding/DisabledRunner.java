package com.gateready.coding;

/** Used when no judge is configured, so code is never run by accident. */
public class DisabledRunner implements CodeRunner {
    @Override
    public Outcome run(String sourceCode, String stdin) {
        return Outcome.of(Status.UNAVAILABLE, "", "No code judge is configured.");
    }
}
