// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap;

import com.github.sftwnd.oracle.unwrap.spi.FormatOption;
import com.github.sftwnd.oracle.unwrap.spi.FormatUnwrapper;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapException;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapOptions;
import com.github.sftwnd.oracle.unwrap.spi.WrappedUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;

/**
 * Restores PL/SQL source code from the Oracle wrapped format.
 * <p>
 * The format of each unit is detected by its header. The formats are the providers of {@link FormatUnwrapper} found by
 * {@link ServiceLoader}, so they depend on what is on the class path or the module path; {@link #formats()} lists them.
 * The object is immutable and thread-safe.
 */
public final class Unwrapper {

    /** Formats this unwrapper restores, by name. */
    private final Map<String, FormatUnwrapper> formats;

    /** Everything that looks for units in a text: the formats and the rejector of the format V1 if it is not one. */
    private final List<FormatUnwrapper> readers;

    /** An unwrapper with {@link UnwrapOptions#defaults()}. */
    public Unwrapper() {
        this(UnwrapOptions.defaults());
    }

    /**
     * An unwrapper with the given options.
     *
     * @throws IllegalStateException if no format implementation is found
     */
    public Unwrapper(UnwrapOptions options) {
        Objects.requireNonNull(options, "options");
        formats = load(options);
        List<FormatUnwrapper> all = new ArrayList<>(formats.values());
        if (!formats.containsKey(V1UnitRejector.FORMAT)) {
            all.add(new V1UnitRejector());
        }
        readers = Collections.unmodifiableList(all);
    }

    /** The formats in the order of their names; a second implementation of a format is ignored. */
    private static Map<String, FormatUnwrapper> load(UnwrapOptions options) {
        Map<String, FormatUnwrapper> found = new TreeMap<>();
        for (FormatUnwrapper format : ServiceLoader.load(FormatUnwrapper.class)) {
            found.putIfAbsent(format.format(), format.withOptions(options));
        }
        if (found.isEmpty()) {
            throw new IllegalStateException("No wrapped format implementation is found: no provider of "
                    + FormatUnwrapper.class.getName());
        }
        return Collections.unmodifiableMap(found);
    }

    /** The formats this unwrapper restores: names, in order, and what each one covers. */
    public Map<String, String> formats() {
        Map<String, String> result = new LinkedHashMap<>();
        formats.forEach((name, format) -> result.put(name, format.description()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * The options of the formats that have any, by the name of the format, in the order of the names: what
     * {@link UnwrapOptions#withFormatOption} accepts.
     */
    public Map<String, List<FormatOption>> formatOptions() {
        Map<String, List<FormatOption>> result = new LinkedHashMap<>();
        formats.forEach((name, format) -> {
            List<FormatOption> options = format.options();
            if (!options.isEmpty()) {
                result.put(name, Collections.unmodifiableList(new ArrayList<>(options)));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /** Whether the text contains a wrapped unit of a supported format; only the header is checked. */
    public boolean supports(String source) {
        Objects.requireNonNull(source, "source");
        return nearest(source, 0) != null;
    }

    /**
     * Unwraps the first wrapped unit of the text.
     *
     * @return the CREATE statement with the terminating {@code /} line
     * @throws UnwrapException if the text has no wrapped units or the first one cannot be unwrapped
     */
    public String unwrap(String source) {
        Objects.requireNonNull(source, "source");
        Candidate first = nearest(source, 0);
        if (first == null) {
            throw new UnwrapException("No wrapped unit found");
        }
        return first.reader().read(source, first.start()).unwrap();
    }

    /**
     * Unwraps all wrapped units of the text in text order.
     *
     * @throws UnwrapException if any unit cannot be parsed or unwrapped
     */
    public List<UnwrapResult> unwrapUnits(String source) {
        Objects.requireNonNull(source, "source");
        List<UnwrapResult> results = new ArrayList<>();
        for (Located located : units(source)) {
            WrappedUnit unit = located.unit();
            results.add(new UnwrapResult(located.format(), unit.plsqlVersion(), unit.plsqlCompatible(),
                    unit.staticSqlAsText(), unit.unitType(), unit.unitName(), unit.unwrap()));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Unwraps an SQL script: each wrapped unit together with its terminating {@code /} line is replaced with the
     * restored text; the rest of the text is copied unchanged, line endings included, and if the {@code /} line of the
     * last unit ends the script without a line break, the restored script ends the same way. After a V2 unit a
     * {@code --} line is written before each line that follows a {@code /} line and starts with blanks: after a PL/SQL
     * block WRAP 10.2 and later keep such blanks only after a comment line, which they remove.
     *
     * @throws UnwrapException if any unit cannot be parsed or unwrapped
     */
    public String unwrapScript(String script) {
        Objects.requireNonNull(script, "script");
        List<WrappedUnit> units = new ArrayList<>();
        for (Located located : units(script)) {
            units.add(located.unit());
        }
        return new ScriptUnwrapper(script, units).unwrap();
    }

    private List<Located> units(String text) {
        List<Located> units = new ArrayList<>();
        int position = 0;
        for (Candidate next = nearest(text, 0); next != null; next = nearest(text, position)) {
            WrappedUnit unit = next.reader().read(text, next.start());
            if (unit.end() <= next.start()) {
                throw new IllegalStateException("Wrapped unit of " + next.reader().format() + " has no content");
            }
            units.add(new Located(next.reader().format(), unit));
            position = unit.end();
        }
        return units;
    }

    /** Nearest unit header across all formats, or {@code null} if there are no more units. */
    private Candidate nearest(String text, int from) {
        Candidate nearest = null;
        for (FormatUnwrapper reader : readers) {
            int start = reader.find(text, from);
            if (start >= 0 && (nearest == null || start < nearest.start())) {
                nearest = new Candidate(reader, start);
            }
        }
        return nearest;
    }

    private static final class Candidate {
        private final FormatUnwrapper reader;
        private final int start;

        Candidate(FormatUnwrapper reader, int start) {
            this.reader = reader;
            this.start = start;
        }

        FormatUnwrapper reader() {
            return reader;
        }

        int start() {
            return start;
        }
    }

    private static final class Located {
        private final String format;
        private final WrappedUnit unit;

        Located(String format, WrappedUnit unit) {
            this.format = format;
            this.unit = unit;
        }

        String format() {
            return format;
        }

        WrappedUnit unit() {
            return unit;
        }
    }
}
