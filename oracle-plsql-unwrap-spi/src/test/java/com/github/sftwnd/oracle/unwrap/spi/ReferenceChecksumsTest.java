// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceChecksumsTest {

    /** SHA-256 of {@code abc}. */
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path directory;

    @Test
    void referencesAsListedHaveNoMismatches() throws IOException {
        write("a.wrapped", "abc");
        write("range/a.unwrapped", "abc");
        write("COVERAGE.md", "not a reference");
        manifest(ABC + "  a.wrapped", ABC + "  range/a.unwrapped");
        assertEquals(Collections.emptyList(), ReferenceChecksums.mismatches(directory));
    }

    @Test
    void changedLineBreaksAreReported() throws IOException {
        write("a.wrapped", "a\r\nb\nc");
        manifest(ABC + "  a.wrapped");
        assertEquals(Collections.singletonList("a.wrapped: checksum differs, line breaks CRLF and LF"),
                ReferenceChecksums.mismatches(directory));
    }

    @Test
    void missingAndUnlistedReferencesAreReported() throws IOException {
        write("range/b.unwrapped", "abc");
        manifest(ABC + "  a.wrapped");
        assertEquals(Arrays.asList("a.wrapped: missing", "range/b.unwrapped: not listed"),
                ReferenceChecksums.mismatches(directory));
    }

    @Test
    void linesOfAnotherFormAndRepeatedPathsAreReported() throws IOException {
        write("a.wrapped", "abc");
        manifest(ABC + " *a.wrapped", ABC + "  a.wrapped", ABC + "  a.wrapped");
        assertEquals(Arrays.asList("SHA256SUMS line 1: not a line of sha256sum", "a.wrapped: listed twice"),
                ReferenceChecksums.mismatches(directory));
    }

    @Test
    void lineBreaksAreNamed() {
        assertEquals("none", ReferenceChecksums.lineBreaks(bytes("abc")));
        assertEquals("LF", ReferenceChecksums.lineBreaks(bytes("a\nb\n")));
        assertEquals("CRLF and CR", ReferenceChecksums.lineBreaks(bytes("a\r\nb\rc\r")));
    }

    private void write(String path, String content) throws IOException {
        Path file = directory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes(content));
    }

    private void manifest(String... lines) throws IOException {
        write(ReferenceChecksums.MANIFEST, String.join("\n", lines) + "\n");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
