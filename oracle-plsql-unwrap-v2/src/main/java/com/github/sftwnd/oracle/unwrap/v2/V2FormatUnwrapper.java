// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.v2;

import com.github.sftwnd.oracle.unwrap.spi.FormatOption;
import com.github.sftwnd.oracle.unwrap.spi.FormatUnwrapper;
import com.github.sftwnd.oracle.unwrap.spi.SourceLines;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapOptions;
import com.github.sftwnd.oracle.unwrap.spi.WrappedUnit;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Format V2 (Oracle 10g and later): the unit holds the source text normalized by WRAP, compressed and enciphered by a
 * byte substitution; the text is restored with the header of the unit. A stray CR of the text ({@code V2StrayCr})
 * becomes a space unless the option {@value #STRAY_CR_OPTION} of {@link UnwrapOptions#withFormatOption} or the system
 * property {@value #STRAY_CR_PROPERTY} says {@code keep}; the option wins over the property.
 */
public final class V2FormatUnwrapper implements FormatUnwrapper {

    /**
     * System property with the stray CR of the text: {@code space} (the default) or {@code keep}, in any case:
     * {@code -Dcom.github.sftwnd.oracle.unwrap.v2.strayCr=keep}. It is read for every unit, so it applies to the units
     * read after it is set.
     */
    public static final String STRAY_CR_PROPERTY = "com.github.sftwnd.oracle.unwrap.v2.strayCr";

    /** Name of the option with the stray CR of the text, the values of {@link #STRAY_CR_PROPERTY}. */
    public static final String STRAY_CR_OPTION = "strayCr";

    private static final String FORMAT = "V2";
    private static final String STRAY_CR_EXPECTED = "space or keep";

    private static final List<FormatOption> OPTIONS = Collections.singletonList(new FormatOption(STRAY_CR_OPTION,
            "stray CR of the text: a space or kept as stored, space by default", "space", "keep"));

    private final V2UnitParser parser = new V2UnitParser();

    /** The stray CR set as an option; {@code null} where the system property decides. */
    private final StrayCr strayCrOption;

    /** V2 whose stray CR comes from the system property. */
    public V2FormatUnwrapper() {
        this(null);
    }

    /**
     * V2 with the option {@value #STRAY_CR_OPTION} set to {@code keep} or {@code space}.
     *
     * @param keepStrayCr whether a stray CR of the text is kept as the unit holds it
     * @deprecated use {@code withOptions(UnwrapOptions.defaults().withFormatOption("V2", "strayCr", "keep"))}; the
     *             constructor is removed in the next major release
     */
    @Deprecated
    public V2FormatUnwrapper(boolean keepStrayCr) {
        this(keepStrayCr ? StrayCr.KEEP : StrayCr.SPACE);
    }

    private V2FormatUnwrapper(StrayCr strayCrOption) {
        this.strayCrOption = strayCrOption;
    }

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public String description() {
        return "Oracle 10g and later";
    }

    @Override
    public List<FormatOption> options() {
        return OPTIONS;
    }

    @Override
    public FormatUnwrapper withOptions(UnwrapOptions options) {
        String value = options.formatOption(FORMAT, STRAY_CR_OPTION);
        return new V2FormatUnwrapper(value == null ? null : constant("Option " + FORMAT + '.' + STRAY_CR_OPTION, value));
    }

    @Override
    public int find(String text, int from) {
        SourceLines.checkOffset(text, from);
        return parser.find(text, from);
    }

    @Override
    public WrappedUnit read(String text, int start) {
        SourceLines.checkOffset(text, start);
        return parser.read(text, start, strayCr() == StrayCr.KEEP);
    }

    /**
     * The stray CR set by the option, else by the system property {@value #STRAY_CR_PROPERTY}, else by its alias
     * {@code UnwrapOptions.KEEP_STRAY_CR_PROPERTY} ({@code true} keeps it); {@link StrayCr#SPACE} without them.
     *
     * @throws IllegalStateException if the property has another value: the command line reports it as an error
     */
    @SuppressWarnings("deprecation")
    private StrayCr strayCr() {
        if (strayCrOption != null) {
            return strayCrOption;
        }
        String value = System.getProperty(STRAY_CR_PROPERTY);
        if (value != null && !value.trim().isEmpty()) {
            return constant("System property " + STRAY_CR_PROPERTY, value);
        }
        return Boolean.getBoolean(UnwrapOptions.KEEP_STRAY_CR_PROPERTY) ? StrayCr.KEEP : StrayCr.SPACE;
    }

    /** The constant named by {@code value} in any case; {@code source} names the option or the property. */
    private static StrayCr constant(String source, String value) {
        String constant = value.trim().toUpperCase(Locale.ROOT);
        for (StrayCr candidate : StrayCr.values()) {
            if (candidate.name().equals(constant)) {
                return candidate;
            }
        }
        throw new IllegalStateException(source + " must be " + STRAY_CR_EXPECTED + ", not " + value);
    }
}
