// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatOptionTest {

    @Test
    void describesAnOptionWithValues() {
        FormatOption option = new FormatOption("layout", "layout of the text", "source", "canonical");

        assertEquals("layout", option.name());
        assertEquals("layout of the text", option.description());
        assertEquals(Arrays.asList("source", "canonical"), option.values());
        assertEquals("FormatOption[layout]", option.toString());
    }

    @Test
    void describesAnOptionOfAnyValue() {
        assertEquals(Collections.emptyList(), new FormatOption("name", "text").values());
    }

    @Test
    void keepsValuesOutOfReachOfTheCaller() {
        FormatOption option = new FormatOption("layout", "text", "a");

        assertThrows(UnsupportedOperationException.class, () -> option.values().add("b"));
    }

    @Test
    void rejectsMissingOrEmptyParts() {
        assertThrows(NullPointerException.class, () -> new FormatOption(null, "text"));
        assertThrows(NullPointerException.class, () -> new FormatOption("name", null));
        assertThrows(NullPointerException.class, () -> new FormatOption("name", "text", (String[]) null));
        assertThrows(NullPointerException.class, () -> new FormatOption("name", "text", (String) null));
        assertThrows(IllegalArgumentException.class, () -> new FormatOption(" ", "text"));
        assertThrows(IllegalArgumentException.class, () -> new FormatOption("name", ""));
        assertThrows(IllegalArgumentException.class, () -> new FormatOption("name", "text", " "));
    }
}
