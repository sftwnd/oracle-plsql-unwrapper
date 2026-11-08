// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Samples of a format, the resources {@code /samples/*} of its tests: the wrapped text ({@code .wrapped}) and the
 * restored text ({@code .unwrapped}). The reference text of a synthetic sample ({@code P*}) is verified by wrapping it
 * again: WRAP of the same version gives the same unit. A sample is stored as the bytes WRAP wrote or the unwrapper
 * restored, line breaks and trailing white space included, and its checksum is in {@code /samples/SHA256SUMS}
 * ({@link ReferenceChecksums}). The content is read as ISO-8859-1 so that the comparison is byte-for-byte; a
 * reference of V2 units whose text is in other character sets is stored and read as UTF-8.
 */
public final class Samples {

    private Samples() {
    }

    /**
     * Reference output {@code reference} for the wrapped sample {@code wrapped}. The restored text takes the line break
     * of the wrapped unit; references are stored with CRLF, so for a sample wrapped with LF (WRAP for Linux) the line
     * breaks of the reference become LF. That is the text the round trip of such a sample is checked with.
     */
    public static String expected(String reference, String wrapped) {
        return expected(reference, wrapped, StandardCharsets.ISO_8859_1);
    }

    /** {@link #expected(String, String)} for a reference stored in {@code charset}. */
    public static String expected(String reference, String wrapped, Charset charset) {
        String text = read(reference, charset);
        return read(wrapped).contains("\r") ? text : text.replace("\r\n", "\n");
    }

    public static String read(String name) {
        return read(name, StandardCharsets.ISO_8859_1);
    }

    public static String read(String name, Charset charset) {
        try (InputStream in = Samples.class.getResourceAsStream("/samples/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No sample " + name);
            }
            return new String(bytes(in), charset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count = in.read(buffer); count >= 0; count = in.read(buffer)) {
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
