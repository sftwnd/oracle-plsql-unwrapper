// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.cli;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed arguments of the command line; a wrong one is reported as {@link IllegalArgumentException}. */
final class CommandLine {

    private static final String STANDARD_STREAM = "-";

    private boolean help;
    private Charset inputEncoding = StandardCharsets.ISO_8859_1;
    private Charset outputEncoding;
    private final List<FormatSetting> formatSettings = new ArrayList<>();
    private String input = STANDARD_STREAM;
    private String output = STANDARD_STREAM;

    private CommandLine() {
    }

    static CommandLine parse(String... args) {
        CommandLine line = new CommandLine();
        int files = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--help".equals(arg)) {
                line.help = true;
            } else if ("--keep-stray-cr".equals(arg)) {
                // Alias of the option strayCr=keep of the format V2, kept for the scripts of the release 1.0.0
                line.formatSettings.add(new FormatSetting("V2", "strayCr", "keep"));
            } else if ("--format-option".equals(arg)) {
                line.formatSettings.add(FormatSetting.parse(value(arg, args, ++i, "<format>.<name>=<value>")));
            } else if ("--input-encoding".equals(arg)) {
                line.inputEncoding = charset(arg, args, ++i);
            } else if ("--output-encoding".equals(arg)) {
                line.outputEncoding = charset(arg, args, ++i);
            } else if (arg.length() > 1 && arg.charAt(0) == '-') {
                throw new IllegalArgumentException("Unknown option " + arg);
            } else if (files == 0) {
                line.input = arg;
                files++;
            } else if (files == 1) {
                line.output = arg;
                files++;
            } else {
                throw new IllegalArgumentException("Unexpected argument " + arg);
            }
        }
        return line;
    }

    private static String value(String option, String[] args, int index, String what) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + option + " needs " + what);
        }
        return args[index];
    }

    private static Charset charset(String option, String[] args, int index) {
        String name = value(option, args, index, "an encoding");
        try {
            return Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException("Unknown encoding " + name, e);
        }
    }

    boolean help() {
        return help;
    }

    /** The options of the formats in the order of the command line; a later one of the same name wins. */
    List<FormatSetting> formatSettings() {
        return Collections.unmodifiableList(formatSettings);
    }

    Charset inputEncoding() {
        return inputEncoding;
    }

    /** The encoding of the result: the one of the input unless it is given. */
    Charset outputEncoding() {
        return outputEncoding != null ? outputEncoding : inputEncoding;
    }

    /** The input file, or {@code null} for the standard input. */
    String input() {
        return STANDARD_STREAM.equals(input) ? null : input;
    }

    /** The output file, or {@code null} for the standard output. */
    String output() {
        return STANDARD_STREAM.equals(output) ? null : output;
    }

    /** An option of a format as the command line gives it: {@code V1.layout=canonical}. */
    static final class FormatSetting {
        private final String format;
        private final String name;
        private final String value;

        private FormatSetting(String format, String name, String value) {
            this.format = format;
            this.name = name;
            this.value = value;
        }

        static FormatSetting parse(String text) {
            int equals = text.indexOf('=');
            int dot = text.indexOf('.');
            if (dot < 1 || equals < dot + 2 || equals == text.length() - 1) {
                throw new IllegalArgumentException("Option --format-option needs <format>.<name>=<value>, not " + text);
            }
            return new FormatSetting(text.substring(0, dot), text.substring(dot + 1, equals),
                    text.substring(equals + 1));
        }

        String format() {
            return format;
        }

        String name() {
            return name;
        }

        String value() {
            return value;
        }

        @Override
        public String toString() {
            return format + '.' + name + '=' + value;
        }
    }
}
