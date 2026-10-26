package com.gateready.coding;

/** Decides whether a program's output matches the expected output. Pure logic. */
public final class OutputComparer {
    private OutputComparer() {}

    /** Ignores line-ending style, trailing spaces on each line and blank lines at the end. Everything else must match. */
    public static boolean matches(String expected, String actual) {
        return normalise(expected).equals(normalise(actual));
    }

    static String normalise(String s) {
        if (s == null) return "";
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line.stripTrailing()).append('\n');
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '\n') end--;
        return sb.substring(0, end);
    }
}
