// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Options of the unwrapper; a format takes those it needs in {@link FormatUnwrapper#withOptions}. The object is
 * immutable: each {@code with} method returns options with one value changed, so options can be added later without
 * breaking the code that sets the others.
 */
public final class UnwrapOptions {

    /**
     * System property with the value of {@link #keepStrayCr()} in {@link #defaults()}:
     * {@code -Dcom.github.sftwnd.oracle.unwrap.keepStrayCr=true}.
     */
    public static final String KEEP_STRAY_CR_PROPERTY = "com.github.sftwnd.oracle.unwrap.keepStrayCr";

    private final boolean keepStrayCr;

    /** Options of the formats by {@code format.name}; the formats that do not know a name ignore it. */
    private final Map<String, String> formatOptions;

    private UnwrapOptions(boolean keepStrayCr, Map<String, String> formatOptions) {
        this.keepStrayCr = keepStrayCr;
        this.formatOptions = formatOptions;
    }

    /**
     * The defaults: a stray CR of a V2 text becomes a space unless the system property {@value #KEEP_STRAY_CR_PROPERTY}
     * is {@code true}, in any case. The property is read on each call, so it applies to the unwrappers created after it
     * is set; options set by a {@code with} method do not depend on it. No option of a format is set.
     */
    public static UnwrapOptions defaults() {
        return new UnwrapOptions(Boolean.getBoolean(KEEP_STRAY_CR_PROPERTY), Collections.emptyMap());
    }

    /**
     * Whether a stray CR of a V2 text is kept as the unit holds it. WRAP 10.2 to 11.2 replace the CR of a source with
     * CRLF with a space, except where it stood in the last byte of a block of 512 bytes of the source file; such a CR
     * outside string literals and quoted identifiers is stray, and by default it becomes a space (see
     * {@link #defaults()}).
     */
    public boolean keepStrayCr() {
        return keepStrayCr;
    }

    /** These options with {@link #keepStrayCr()} set to {@code keep}. */
    public UnwrapOptions withKeepStrayCr(boolean keep) {
        return keep == keepStrayCr ? this : new UnwrapOptions(keep, formatOptions);
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
        Map<String, String> changed = new TreeMap<>(formatOptions);
        changed.put(key(format, name), value);
        return new UnwrapOptions(keepStrayCr, Collections.unmodifiableMap(changed));
    }

    private static String key(String format, String name) {
        return Objects.requireNonNull(format, "format") + '.' + Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UnwrapOptions
                && ((UnwrapOptions) other).keepStrayCr == keepStrayCr
                && ((UnwrapOptions) other).formatOptions.equals(formatOptions);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(keepStrayCr) + formatOptions.hashCode();
    }

    @Override
    public String toString() {
        return "UnwrapOptions[keepStrayCr=" + keepStrayCr + (formatOptions.isEmpty() ? "" : ", " + formatOptions) + "]";
    }
}
