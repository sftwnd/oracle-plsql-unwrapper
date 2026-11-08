// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.spi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checksums of test references. The manifest {@value #MANIFEST} of a directory lists every reference below it
 * ({@code *.wrapped}, {@code *.unwrapped}) with its SHA-256, a line {@code <hash>  <path>} each, as {@code sha256sum}
 * writes it. The tests compare the references byte for byte, but a conversion of line breaks on checkout changes the
 * wrapped unit and its expected text alike: the tests still pass, without testing the line break the unit was written
 * with. Only the checksums show such a change, and a change made by an editor. The manifest is rebuilt in its
 * directory with
 * <pre>
 * find . -type f \( -name '*.wrapped' -o -name '*.unwrapped' \) | sed 's|^\./||' | LC_ALL=C sort \
 *     | xargs shasum -a 256 &gt; SHA256SUMS
 * </pre>
 */
public final class ReferenceChecksums {

    /** File name of the manifest in a directory of references. */
    public static final String MANIFEST = "SHA256SUMS";

    private static final Pattern LINE = Pattern.compile("([0-9a-f]{64})  (.+)");

    private ReferenceChecksums() {
    }

    /**
     * Differences of the directory {@code directory} from its manifest, one line each: a line of the manifest that is
     * not a line of {@code sha256sum} or lists a path again, a listed file that is missing or has another checksum
     * (with its line breaks now), and a reference that is not listed. An empty list means every reference is as
     * listed.
     */
    public static List<String> mismatches(Path directory) {
        List<String> mismatches = new ArrayList<>();
        Map<String, String> listed = new TreeMap<>();
        List<String> lines = lines(directory.resolve(MANIFEST));
        for (int i = 0; i < lines.size(); i++) {
            Matcher line = LINE.matcher(lines.get(i));
            if (!line.matches()) {
                mismatches.add(MANIFEST + " line " + (i + 1) + ": not a line of sha256sum");
            } else if (listed.put(line.group(2), line.group(1)) != null) {
                mismatches.add(line.group(2) + ": listed twice");
            }
        }
        for (Map.Entry<String, String> entry : listed.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            if (!Files.isRegularFile(file)) {
                mismatches.add(entry.getKey() + ": missing");
                continue;
            }
            byte[] bytes = bytes(file);
            if (!entry.getValue().equals(sha256(bytes))) {
                mismatches.add(entry.getKey() + ": checksum differs, line breaks " + lineBreaks(bytes));
            }
        }
        for (String reference : references(directory)) {
            if (!listed.containsKey(reference)) {
                mismatches.add(reference + ": not listed");
            }
        }
        return mismatches;
    }

    /** Paths of the references below {@code directory}, relative to it with {@code /} as the separator. */
    private static Set<String> references(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            String separator = directory.getFileSystem().getSeparator();
            return files.filter(Files::isRegularFile)
                    .map(file -> directory.relativize(file).toString().replace(separator, "/"))
                    .filter(path -> path.endsWith(".wrapped") || path.endsWith(".unwrapped"))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Kinds of line breaks in {@code bytes}: {@code CRLF}, {@code LF} or {@code CR}, several of them joined with
     * {@code " and "} in this order, or {@code none}.
     */
    public static String lineBreaks(byte[] bytes) {
        boolean crlf = false;
        boolean lf = false;
        boolean cr = false;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\r' && i + 1 < bytes.length && bytes[i + 1] == '\n') {
                crlf = true;
                i++;
            } else if (bytes[i] == '\r') {
                cr = true;
            } else if (bytes[i] == '\n') {
                lf = true;
            }
        }
        List<String> kinds = new ArrayList<>();
        if (crlf) {
            kinds.add("CRLF");
        }
        if (lf) {
            kinds.add("LF");
        }
        if (cr) {
            kinds.add("CR");
        }
        return kinds.isEmpty() ? "none" : String.join(" and ", kinds);
    }

    static String sha256(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
