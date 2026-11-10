// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Description of an option that only one format has, for the help of the command line: the name, what the option does
 * and the values it accepts. The value is set with {@link UnwrapOptions#withFormatOption}. The object is immutable.
 */
public final class FormatOption {

    private final String name;
    private final String description;
    private final List<String> values;

    /**
     * @param name        name of the option, unique within the format, for example {@code layout}
     * @param description what the option does and its default, one line
     * @param values      the accepted values; none for a value of any kind
     */
    public FormatOption(String name, String description, String... values) {
        this.name = requireText(name, "name");
        this.description = requireText(description, "description");
        List<String> accepted = new ArrayList<>(Arrays.asList(Objects.requireNonNull(values, "values")));
        accepted.forEach(value -> requireText(value, "value"));
        this.values = Collections.unmodifiableList(accepted);
    }

    private static String requireText(String text, String what) {
        if (Objects.requireNonNull(text, what).trim().isEmpty()) {
            throw new IllegalArgumentException("The " + what + " of a format option is empty");
        }
        return text;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** The accepted values in the order of the help; empty if the option takes a value of any kind. */
    public List<String> values() {
        return values;
    }

    @Override
    public String toString() {
        return "FormatOption[" + name + "]";
    }
}
