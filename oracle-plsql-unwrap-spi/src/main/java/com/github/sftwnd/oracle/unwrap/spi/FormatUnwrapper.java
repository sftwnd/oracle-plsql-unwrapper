// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import java.util.Collections;
import java.util.List;

/**
 * Implementation of one wrapped format: finds units in the text and parses them.
 * <p>
 * The unwrapper finds the implementations with {@link java.util.ServiceLoader}, queries all of them and takes the
 * nearest unit, so one script may contain units of different formats. An implementation is a service provider: it has
 * a public constructor without parameters and is declared in {@code META-INF/services} and in its
 * {@code module-info}. It must be thread-safe.
 */
public interface FormatUnwrapper {

    /** Name of the format, for example {@code V2}; the unwrapper keeps one implementation of a format. */
    String format();

    /** What the format covers, for the user: the Oracle releases that write it. */
    String description();

    /**
     * The options of this format that {@link #withOptions} takes from {@link UnwrapOptions#formatOption}; the help of
     * the command line lists them. A format that has none returns an empty list.
     */
    default List<FormatOption> options() {
        return Collections.emptyList();
    }

    /**
     * This format with the given options; a format that has no options returns itself.
     *
     * @return the implementation to use, this one or a new one
     * @throws IllegalStateException if the value of an option of this format is not accepted
     */
    default FormatUnwrapper withOptions(UnwrapOptions options) {
        return this;
    }

    /**
     * Finds the header of the next unit of this format. A unit header starts on a new line; only spaces and tabs
     * may precede it. A format may also return a unit whose header it cannot read, for {@link #read} to report it:
     * such a unit does not stay wrapped in a restored script without an error.
     *
     * @return offset of the header start not before {@code from}, or -1 if there are no more units of this format
     */
    int find(String text, int from);

    /**
     * Parses the unit whose header {@link #find} has found at offset {@code start}.
     *
     * @throws UnwrapException if the unit content does not match the format
     */
    WrappedUnit read(String text, int start);
}
