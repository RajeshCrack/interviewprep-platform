package com.gateready.coding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputComparerTest {
    @Test void identicalOutputMatches() { assertThat(OutputComparer.matches("42", "42")).isTrue(); }

    @Test void ignoresTrailingNewlinesAndBlankLinesAtTheEnd() {
        assertThat(OutputComparer.matches("42", "42\n")).isTrue();
        assertThat(OutputComparer.matches("42\n", "42\n\n\n")).isTrue();
    }

    @Test void ignoresWindowsLineEndings() { assertThat(OutputComparer.matches("1 2\n3 4", "1 2\r\n3 4\r\n")).isTrue(); }

    @Test void ignoresTrailingSpacesOnEachLine() { assertThat(OutputComparer.matches("1 2\n3 4", "1 2   \n3 4 ")).isTrue(); }

    @Test void leadingSpacesAndInnerSpacesStillMatter() {
        assertThat(OutputComparer.matches("1 2", " 1 2")).isFalse();
        assertThat(OutputComparer.matches("1 2", "1  2")).isFalse();
    }

    @Test void differentValuesDoNotMatch() {
        assertThat(OutputComparer.matches("42", "43")).isFalse();
        assertThat(OutputComparer.matches("YES", "yes")).isFalse();
    }

    @Test void aBlankLineInTheMiddleIsADifference() { assertThat(OutputComparer.matches("a\nb", "a\n\nb")).isFalse(); }

    @Test void emptyOutputIsNotACorrectAnswerForANonEmptyExpectation() {
        assertThat(OutputComparer.matches("0", "")).isFalse();
        assertThat(OutputComparer.matches("", null)).isTrue();
    }
}
