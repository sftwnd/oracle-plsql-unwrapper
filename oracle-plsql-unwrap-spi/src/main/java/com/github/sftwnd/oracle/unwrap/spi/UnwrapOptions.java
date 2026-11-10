// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Options of the unwrapper; a format takes those it needs in {@link FormatUnwrapper#withOptions}. The object is
 * immutable: each {@code with} method returns options with one value changed, so options can be added later without
 * breaking the code that sets the others. Every option belongs to a format and is set by its name with
 * {@link #withFormatOption}; what is not set is decided by the format, usually from its system property.
 */
public final class UnwrapOptions {

    /**
     * System property with the default of the stray CR of a V2 text: {@code true} in any case keeps it, as the option
     * {@code strayCr=keep} of the format V2 does. The format reads it as an alias of its own property
     * {@code com.github.sftwnd.oracle.unwrap.v2.strayCr}.
     *
     * @deprecated use the property {@code com.github.sftwnd.oracle.unwrap.v2.strayCr} with the value {@code keep};
     *             the alias is removed in the next major release
     */
    @Deprecated
    public static final String KEEP_STRAY_CR_PROPERTY = "com.github.sftwnd.oracle.unwrap.keepStrayCr";

    /** Format and option that {@link #keepStrayCr()} and {@link #withKeepStrayCr} stand for. */
    private static final String V2_FORMAT = "V2";
    private static final String STRAY_CR_OPTION = "strayCr";
    private static final String KEEP = "keep";
    private static final String SPACE = "space";

    /** Options of the formats by {@code format.name}; the formats that do not know a name ignore it. */
    private final Map<String, String> formatOptions;

    private UnwrapOptions(Map<String, String> formatOptions) {
        this.formatOptions = formatOptions;
    }

    /** The defaults: no option of a format is set, so each format decides from its system properties. */
    public static UnwrapOptions defaults() {
        return new UnwrapOptions(Collections.emptyMap());
    }

    /**
     * Whether the option {@code strayCr} of the format V2 is set to {@code keep}: a stray CR of a V2 text is kept as
     * the unit holds it instead of becoming a space.
     *
     * @deprecated use {@code formatOption("V2", "strayCr")}; the method is removed in the next major release
     */
    @Deprecated
    public boolean keepStrayCr() {
        String value = formatOption(V2_FORMAT, STRAY_CR_OPTION);
        return value != null && value.trim().equalsIgnoreCase(KEEP);
    }

    /**
     * These options with the option {@code strayCr} of the format V2 set to {@code keep} or {@code space}.
     *
     * @deprecated use {@code withFormatOption("V2", "strayCr", "keep")}; the method is removed in the next major
     *             release
     */
    @Deprecated
    public UnwrapOptions withKeepStrayCr(boolean keep) {
        return withFormatOption(V2_FORMAT, STRAY_CR_OPTION, keep ? KEEP : SPACE);
    }

    /**
     * The value of the option {@code name} of the format {@code format}, as the format lists it in
     * {@link FormatUnwrapper#options()}.
     *
     * @return the value as it was set, or {@code null} if it is not set
     */
    public String formatOption(String format, String name) {
        return formatOptions.get(key(format, name));
    }

    /**
     * These options with the option {@code name} of the format {@code format} set to {@code value}; the format
     * validates the value in {@link FormatUnwrapper#withOptions}, and a format that has no such option ignores it.
     */
    public UnwrapOptions withFormatOption(String format, String name, String value) {
        Objects.requireNonNull(value, "value");
        String key = key(format, name);
        if (value.equals(formatOptions.get(key))) {
            return this;
        }
        Map<String, String> changed = new TreeMap<>(formatOptions);
        changed.put(key, value);
        return new UnwrapOptions(Collections.unmodifiableMap(changed));
    }

    private static String key(String format, String name) {
        return Objects.requireNonNull(format, "format") + '.' + Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UnwrapOptions && ((UnwrapOptions) other).formatOptions.equals(formatOptions);
    }

    @Override
    public int hashCode() {
        return formatOptions.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("UnwrapOptions[");
        formatOptions.forEach((key, value) -> text.append(text.length() > 14 ? ", " : "").append(key).append('=')
                .append(value));
        return text.append(']').toString();
    }
}
