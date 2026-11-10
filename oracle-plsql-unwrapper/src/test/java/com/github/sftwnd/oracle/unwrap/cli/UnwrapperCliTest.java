// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.cli;

import com.github.sftwnd.oracle.unwrap.spi.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnwrapperCliTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(byte[] input, String... args) {
        return new UnwrapperCli(new ByteArrayInputStream(input), out, new PrintStream(err, true)).run(args);
    }

    private int run(String... args) {
        return run(new byte[0], args);
    }

    private String output() {
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private String errors() {
        return new String(err.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    @Test
    void helpNamesOnlyTheFormatV2() {
        assertEquals(0, run("--help"));

        assertTrue(output().contains("restores only the wrapped formats:\n  V2  Oracle 10g and later\n"), output());
        assertFalse(output().contains("V1"), output());
    }

    /** V2 has no options of its own, so the help neither lists them nor mentions the option. */
    @Test
    void helpHasNoFormatOptionsWithoutThem() {
        assertEquals(0, run("--help"));

        assertFalse(output().contains("--format-option"), output());
    }

    @Test
    void rejectsOptionOfFormatThatHasNone() {
        assertEquals(2, run("--format-option", "V2.layout=canonical"));

        assertEquals("Unknown format option V2.layout\nTry --help\n", errors());
    }

    @Test
    void rejectsOptionOfAbsentFormat() {
        assertEquals(2, run("--format-option", "V1.layout=canonical", "--help"));

        assertEquals("Unknown format option V1.layout\nTry --help\n", errors());
        assertEquals("", output());
    }

    @Test
    void rejectsMalformedFormatOption() {
        assertEquals(2, run("--format-option", "layout"));
        assertEquals("Option --format-option needs <format>.<name>=<value>, not layout\nTry --help\n", errors());
    }

    @Test
    void rejectsFormatOptionWithoutArgument() {
        assertEquals(2, run("--format-option"));
        assertEquals("Option --format-option needs <format>.<name>=<value>\nTry --help\n", errors());
    }

    @Test
    void helpEndsWithTheCopyright() {
        assertEquals(0, run("--help"));

        assertTrue(output().endsWith("\nCopyright (c) 2026, Andrey D. Shindarev\n"), output());
    }

    @Test
    void restoresScriptFromStandardInputToStandardOutput() {
        String wrapped = Samples.read("P1020EDGE.wrapped");

        assertEquals(0, run(wrapped.getBytes(StandardCharsets.ISO_8859_1)));

        assertEquals(Samples.expected("P1020EDGE.unwrapped", "P1020EDGE.wrapped"), output());
    }

    @Test
    void restoresFileToFile(@TempDir Path directory) throws IOException {
        Path input = directory.resolve("in.sql");
        Path output = directory.resolve("out.sql");
        Files.write(input, Samples.read("P1020EDGE.wrapped").getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(0, run(input.toString(), output.toString()));

        assertArrayEquals(Samples.expected("P1020EDGE.unwrapped", "P1020EDGE.wrapped")
                .getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(output));
    }

    @Test
    void keepsStrayCrOnRequest() {
        byte[] wrapped = Samples.read("P1020STRAYCR.wrapped").getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(0, run(wrapped, "--keep-stray-cr"));

        assertEquals(Samples.read("P1020STRAYCRKEPT.unwrapped"), output());
    }

    @Test
    void restoredTextThatDoesNotFitTheEncodingIsAnError() {
        byte[] wrapped = Samples.read("P1020CHARSETS.wrapped").getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(1, run(wrapped, "--output-encoding", "US-ASCII"));

        assertEquals("", output());
        assertTrue(errors().contains("--output-encoding"), errors());
    }

    @Test
    void reportsV1UnitAsFailure() {
        byte[] script = "create package p wrapped\n0\nabcd\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(1, run(script));

        assertTrue(errors().startsWith("Line 1: Unwrapping of the wrapped format V1"), errors());
    }

    @Test
    void missingFileIsFailure(@TempDir Path directory) {
        assertEquals(1, run(directory.resolve("missing.sql").toString()));

        assertTrue(errors().contains("missing.sql"), errors());
    }

    @Test
    void wrongArgumentsAreUsageErrors() {
        assertEquals(2, run("--unknown"));
        assertEquals(2, run("--input-encoding"));
        assertEquals(2, run("--input-encoding", "no-such-encoding"));
        assertEquals(2, run("a", "b", "c"));
    }
}
