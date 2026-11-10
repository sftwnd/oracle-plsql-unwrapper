// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap;

import com.github.sftwnd.oracle.unwrap.spi.Samples;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnwrapOptionsTest {

    /**
     * The name of the property is the package of the API followed by the option. It is written out, so that a move of
     * the package does not change it silently; this test makes such a move a decision about the property too.
     */
    @Test
    void namesThePropertyAfterThePackageAndTheOption() {
        assertEquals("com.github.sftwnd.oracle.unwrap.keepStrayCr", UnwrapOptions.KEEP_STRAY_CR_PROPERTY);
    }

    @Test
    void replacesStrayCrByDefault() {
        withProperty(null, () -> assertFalse(UnwrapOptions.defaults().keepStrayCr()));
    }

    /** {@link Boolean#getBoolean}: {@code true} in any case, and every other value is false. */
    @ParameterizedTest
    @CsvSource({"true, true", "TRUE, true", "True, true", "false, false", "yes, false", "'', false"})
    void takesTheDefaultFromTheSystemProperty(String value, boolean keep) {
        withProperty(value, () -> assertEquals(keep, UnwrapOptions.defaults().keepStrayCr()));
    }

    /** The property sets the default of an unwrapper created after it; an option set explicitly does not use it. */
    @Test
    void keepsStrayCrOfNewUnwrappersWhenThePropertyIsTrue() {
        String wrapped = Samples.read("P1020STRAYCR.wrapped");

        withProperty("true", () -> {
            assertEquals(Samples.read("P1020STRAYCRKEPT.unwrapped"), new Unwrapper().unwrapScript(wrapped));
            assertEquals(Samples.read("P1020STRAYCR.unwrapped"),
                    new Unwrapper(UnwrapOptions.defaults().withKeepStrayCr(false)).unwrapScript(wrapped));
        });
    }

    @Test
    void changesOneValueAndKeepsTheDefaults() {
        withProperty(null, () -> {
            UnwrapOptions keep = UnwrapOptions.defaults().withKeepStrayCr(true);

            assertTrue(keep.keepStrayCr());
            assertFalse(UnwrapOptions.defaults().keepStrayCr());
            assertSame(keep, keep.withKeepStrayCr(true));
            assertEquals(UnwrapOptions.defaults(), keep.withKeepStrayCr(false));
            assertEquals(UnwrapOptions.defaults().hashCode(), keep.withKeepStrayCr(false).hashCode());
            assertNotEquals(UnwrapOptions.defaults(), keep);
        });
    }

    @Test
    void setsOptionsOfFormats() {
        withProperty(null, () -> {
            UnwrapOptions options = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");

            assertEquals("canonical", options.formatOption("V1", "layout"));
            assertNull(options.formatOption("V1", "in"));
            assertNull(options.formatOption("V2", "layout"));
            assertNull(UnwrapOptions.defaults().formatOption("V1", "layout"));
            assertEquals("never", options.withFormatOption("V1", "in", "never").formatOption("V1", "in"));
            assertEquals("canonical", options.withFormatOption("V1", "in", "never").formatOption("V1", "layout"));
            assertEquals("source", options.withFormatOption("V1", "layout", "source").formatOption("V1", "layout"));
            assertEquals("canonical", options.formatOption("V1", "layout"));
        });
    }

    @Test
    void keepsOptionsOfFormatsWhenAnotherValueChanges() {
        withProperty(null, () -> {
            UnwrapOptions options = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");

            assertEquals("canonical", options.withKeepStrayCr(true).formatOption("V1", "layout"));
            assertTrue(options.withFormatOption("V1", "in", "never").keepStrayCr() == options.keepStrayCr());
        });
    }

    @Test
    void comparesOptionsOfFormats() {
        withProperty(null, () -> {
            UnwrapOptions one = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");
            UnwrapOptions same = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");

            assertEquals(one, same);
            assertEquals(one.hashCode(), same.hashCode());
            assertNotEquals(one, UnwrapOptions.defaults());
            assertNotEquals(one, UnwrapOptions.defaults().withFormatOption("V1", "layout", "source"));
            assertEquals("UnwrapOptions[keepStrayCr=false, {V1.layout=canonical}]", one.toString());
            assertEquals("UnwrapOptions[keepStrayCr=false]", UnwrapOptions.defaults().toString());
        });
    }

    @Test
    void rejectsIncompleteOptionOfFormat() {
        UnwrapOptions defaults = UnwrapOptions.defaults();

        assertThrows(NullPointerException.class, () -> defaults.withFormatOption(null, "layout", "x"));
        assertThrows(NullPointerException.class, () -> defaults.withFormatOption("V1", null, "x"));
        assertThrows(NullPointerException.class, () -> defaults.withFormatOption("V1", "layout", null));
        assertThrows(NullPointerException.class, () -> defaults.formatOption(null, "layout"));
    }

    @Test
    void noFormatOptionsWithoutTheFormatsThatHaveThem() {
        assertEquals(Collections.emptyMap(), new Unwrapper().formatOptions());
    }

    @Test
    void rejectsNoOptions() {
        assertThrows(NullPointerException.class, () -> new Unwrapper(null));
    }

    /** Runs {@code test} with the property set to {@code value}, or cleared for {@code null}, and restores it. */
    private static void withProperty(String value, Runnable test) {
        String saved = System.getProperty(UnwrapOptions.KEEP_STRAY_CR_PROPERTY);
        try {
            set(value);
            test.run();
        } finally {
            set(saved);
        }
    }

    private static void set(String value) {
        if (value == null) {
            System.clearProperty(UnwrapOptions.KEEP_STRAY_CR_PROPERTY);
        } else {
            System.setProperty(UnwrapOptions.KEEP_STRAY_CR_PROPERTY, value);
        }
    }
}
