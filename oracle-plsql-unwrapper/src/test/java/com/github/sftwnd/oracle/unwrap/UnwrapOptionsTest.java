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

    /** The property of the format V2, as its class names it; the facade does not refer to the class. */
    private static final String STRAY_CR_PROPERTY = "com.github.sftwnd.oracle.unwrap.v2.strayCr";

    /** The alias of the release 1.0.0. */
    @SuppressWarnings("deprecation")
    private static final String KEEP_STRAY_CR_PROPERTY = UnwrapOptions.KEEP_STRAY_CR_PROPERTY;

    /**
     * The names of the properties are the package of the API and of the format followed by the option. They are
     * written out, so that a move of a package does not change them silently.
     */
    @Test
    void namesThePropertiesAfterThePackageAndTheOption() {
        assertEquals("com.github.sftwnd.oracle.unwrap.keepStrayCr", KEEP_STRAY_CR_PROPERTY);
    }

    @Test
    void setsNoOptionByDefault() {
        withProperties(null, null, () -> {
            assertNull(UnwrapOptions.defaults().formatOption("V2", "strayCr"));
            assertEquals(Samples.read("P1020STRAYCR.unwrapped"),
                    new Unwrapper().unwrapScript(Samples.read("P1020STRAYCR.wrapped")));
        });
    }

    /** The property of the format sets the default of the units read after it; an option set explicitly wins. */
    @ParameterizedTest
    @CsvSource({"keep, true", "KEEP, true", " Keep , true", "space, false", "'', false"})
    void takesTheDefaultFromThePropertyOfTheFormat(String value, boolean keep) {
        String wrapped = Samples.read("P1020STRAYCR.wrapped");

        withProperties(value, null, () -> {
            assertEquals(Samples.read(keep ? "P1020STRAYCRKEPT.unwrapped" : "P1020STRAYCR.unwrapped"),
                    new Unwrapper().unwrapScript(wrapped));
            assertEquals(Samples.read("P1020STRAYCR.unwrapped"),
                    new Unwrapper(UnwrapOptions.defaults().withFormatOption("V2", "strayCr", "space"))
                            .unwrapScript(wrapped));
            assertEquals(Samples.read("P1020STRAYCRKEPT.unwrapped"),
                    new Unwrapper(UnwrapOptions.defaults().withFormatOption("V2", "strayCr", "keep"))
                            .unwrapScript(wrapped));
        });
    }

    /** {@link Boolean#getBoolean}: {@code true} in any case, and every other value is false. */
    @ParameterizedTest
    @CsvSource({"true, true", "TRUE, true", "false, false", "yes, false", "'', false"})
    void takesTheDefaultFromTheAliasProperty(String value, boolean keep) {
        withProperties(null, value, () -> assertEquals(
                Samples.read(keep ? "P1020STRAYCRKEPT.unwrapped" : "P1020STRAYCR.unwrapped"),
                new Unwrapper().unwrapScript(Samples.read("P1020STRAYCR.wrapped"))));
    }

    @Test
    void propertyOfTheFormatWinsOverTheAlias() {
        withProperties("space", "true", () -> assertEquals(Samples.read("P1020STRAYCR.unwrapped"),
                new Unwrapper().unwrapScript(Samples.read("P1020STRAYCR.wrapped"))));
    }

    @Test
    void rejectsWrongValueOfThePropertyOfTheFormat() {
        withProperties("yes", null, () -> {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new Unwrapper().unwrapScript(Samples.read("P1020STRAYCR.wrapped")));
            assertEquals("System property " + STRAY_CR_PROPERTY + " must be space or keep, not yes", e.getMessage());
        });
    }

    @Test
    void rejectsWrongValueOfTheOption() {
        UnwrapOptions options = UnwrapOptions.defaults().withFormatOption("V2", "strayCr", "yes");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new Unwrapper(options));
        assertEquals("Option V2.strayCr must be space or keep, not yes", e.getMessage());
    }

    /** The methods of the release 1.0.0 stand for the option {@code strayCr} of the format V2. */
    @Test
    @SuppressWarnings("deprecation")
    void keepsStrayCrThroughTheMethodsOfTheFirstRelease() {
        UnwrapOptions keep = UnwrapOptions.defaults().withKeepStrayCr(true);

        assertTrue(keep.keepStrayCr());
        assertEquals("keep", keep.formatOption("V2", "strayCr"));
        assertFalse(UnwrapOptions.defaults().keepStrayCr());
        assertFalse(keep.withKeepStrayCr(false).keepStrayCr());
        assertEquals("space", keep.withKeepStrayCr(false).formatOption("V2", "strayCr"));
        assertTrue(UnwrapOptions.defaults().withFormatOption("V2", "strayCr", " Keep ").keepStrayCr());
        assertSame(keep, keep.withKeepStrayCr(true));
        assertEquals(keep, UnwrapOptions.defaults().withFormatOption("V2", "strayCr", "keep"));
        assertNotEquals(UnwrapOptions.defaults(), keep);
        withProperties(null, null, () -> assertEquals(Samples.read("P1020STRAYCRKEPT.unwrapped"),
                new Unwrapper(keep).unwrapScript(Samples.read("P1020STRAYCR.wrapped"))));
    }

    @Test
    void setsOptionsOfFormats() {
        UnwrapOptions options = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");

        assertEquals("canonical", options.formatOption("V1", "layout"));
        assertNull(options.formatOption("V1", "in"));
        assertNull(options.formatOption("V2", "layout"));
        assertNull(UnwrapOptions.defaults().formatOption("V1", "layout"));
        assertEquals("never", options.withFormatOption("V1", "in", "never").formatOption("V1", "in"));
        assertEquals("canonical", options.withFormatOption("V1", "in", "never").formatOption("V1", "layout"));
        assertEquals("source", options.withFormatOption("V1", "layout", "source").formatOption("V1", "layout"));
        assertEquals("canonical", options.formatOption("V1", "layout"));
        assertSame(options, options.withFormatOption("V1", "layout", "canonical"));
    }

    @Test
    void comparesOptionsOfFormats() {
        UnwrapOptions one = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");
        UnwrapOptions same = UnwrapOptions.defaults().withFormatOption("V1", "layout", "canonical");

        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
        assertNotEquals(one, UnwrapOptions.defaults());
        assertNotEquals(one, UnwrapOptions.defaults().withFormatOption("V1", "layout", "source"));
        assertEquals("UnwrapOptions[V1.layout=canonical]", one.toString());
        assertEquals("UnwrapOptions[V1.in=never, V1.layout=canonical]",
                one.withFormatOption("V1", "in", "never").toString());
        assertEquals("UnwrapOptions[]", UnwrapOptions.defaults().toString());
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
    void listsTheOptionOfTheFormatV2() {
        assertEquals(Collections.singleton("V2"), new Unwrapper().formatOptions().keySet());
        assertEquals("strayCr", new Unwrapper().formatOptions().get("V2").get(0).name());
    }

    @Test
    void rejectsNoOptions() {
        assertThrows(NullPointerException.class, () -> new Unwrapper(null));
    }

    /**
     * Runs {@code test} with the property of the format and its alias set to the values, or cleared for {@code null},
     * and restores them.
     */
    private static void withProperties(String strayCr, String keepStrayCr, Runnable test) {
        String savedStrayCr = System.getProperty(STRAY_CR_PROPERTY);
        String savedKeepStrayCr = System.getProperty(KEEP_STRAY_CR_PROPERTY);
        try {
            set(STRAY_CR_PROPERTY, strayCr);
            set(KEEP_STRAY_CR_PROPERTY, keepStrayCr);
            test.run();
        } finally {
            set(STRAY_CR_PROPERTY, savedStrayCr);
            set(KEEP_STRAY_CR_PROPERTY, savedKeepStrayCr);
        }
    }

    private static void set(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
