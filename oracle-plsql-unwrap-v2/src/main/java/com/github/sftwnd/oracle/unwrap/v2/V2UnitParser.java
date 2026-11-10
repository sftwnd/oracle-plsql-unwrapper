// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.v2;

import com.github.sftwnd.oracle.unwrap.spi.UnwrapException;
import com.github.sftwnd.oracle.unwrap.spi.SourceLines;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds and reads V2 units. A unit is the header, a line {@code a000000}, the character set ID, the lines
 * {@code abcd}, the unit kind, the text lengths and the Base64 lines of the data, all values hexadecimal. Thread-safe.
 */
final class V2UnitParser {

    /** Line after the header of a V2 unit; a V1 unit has {@code 0} there. */
    private static final String MARKER = "a000000";

    /** Filler lines between the character set ID and the unit kind. */
    private static final String FILLER = "abcd";

    /** Last word of the header. */
    private static final String WRAPPED = "WRAPPED";

    /** Clause of a unit of Oracle 12c and later: {@code SHARING = value}. */
    private static final String SHARING = "SHARING";

    private static final Set<String> KINDS = setOf("PACKAGE", "TYPE", "PROCEDURE", "FUNCTION", "LIBRARY", "ASSEMBLY");
    private static final Set<String> KINDS_WITH_BODY = setOf("PACKAGE", "TYPE");
    private static final Set<String> EDITIONABILITY = setOf("EDITIONABLE", "NONEDITIONABLE");

    /** Words after {@code IF} between the kind and the name of a unit of WRAP 23.26.1. */
    private static final List<String> NOT_EXISTS = Collections.unmodifiableList(Arrays.asList("NOT", "EXISTS"));

    private static final String TIMESTAMP = "TIMESTAMP";

    /**
     * Clauses after the name in the order WRAP accepts them. Each is followed by a string literal; TIMESTAMP may be
     * followed by {@code OLD} instead.
     */
    private static final List<String> CLAUSES = Collections.unmodifiableList(Arrays.asList(TIMESTAMP, "OID"));

    /**
     * What WRAP 10.1 copies after the last clause of a unit other than a type if the clause ends its line: it reads the
     * source line by line and finds the end of the line there, while for a type it has already read the next line.
     */
    private static final char LINE_END = '\0';

    /** What WRAP 10.1 copies after the last clause; a CRLF comes from a change of the line endings. */
    private static final Set<String> CLAUSE_ENDS = setOf(" ", "\t", "\n", "\r\n", String.valueOf(LINE_END));

    /** Most hexadecimal digits of a value: seven always fit an {@code int}. */
    private static final int MAX_HEX_DIGITS = 7;

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /** The line without the white space at its end; {@link Character#isWhitespace} is the rule, not {@code trim}. */
    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    /** The line without the white space at its ends. */
    private static String strip(String line) {
        String stripped = stripTrailing(line);
        int start = 0;
        while (start < stripped.length() && Character.isWhitespace(stripped.charAt(start))) {
            start++;
        }
        return stripped.substring(start);
    }

    /**
     * Finds the header of a V2 unit at a line start not before {@code from}: {@code CREATE ... wrapped} or, in text
     * taken from the data dictionary, {@code PACKAGE ... wrapped}, followed by the line {@code a000000}. A header that
     * cannot be read is found too, by its last line: only a unit has the lines after it, and {@link #read} reports it,
     * so that the unit does not stay wrapped in the restored script.
     *
     * @return offset of the first word of the header, or -1 if there are no more units
     */
    int find(String text, int from) {
        for (int line = from; line < text.length(); line = nextLine(text, line)) {
            int first = line;
            while (first < text.length() && (text.charAt(first) == ' ' || text.charAt(first) == '\t')) {
                first++;
            }
            if (header(text, first) != null || endsUnknownHeader(text, line)) {
                return first;
            }
        }
        return -1;
    }

    /**
     * Reads the unit whose header {@link #find} has found at {@code start}, up to the end of its Base64 lines.
     *
     * @param keepStrayCr whether the unit keeps a stray CR of its text ({@link V2StrayCr})
     * @throws UnwrapException if the unit does not match the V2 format
     */
    V2Unit read(String text, int start, boolean keepStrayCr) {
        Header header = header(text, start);
        if (header == null) {
            throw new UnwrapException("Unknown V2 unit header (line " + SourceLines.lineNumber(text, start) + ")");
        }
        Lines lines = new Lines(text, header.dataStart());
        int characterSet = lines.hex(lines.next("character set ID"), "character set ID");
        String kind = lines.next("unit kind");
        while (kind.equals(FILLER)) {
            kind = lines.next("unit kind");
        }
        lines.hex(kind, "unit kind");
        String[] lengths = lines.next("text lengths").split("[ \t]+");
        if (lengths.length != 2) {
            throw lines.error("Expected two text lengths in the V2 unit");
        }
        // The second length counts the Base64 characters and LF line breaks, so it changes with the line endings
        int textLength = lines.hex(lengths[0], "text length");
        lines.hex(lengths[1], "Base64 length");

        int dataStart = lines.offset();
        StringBuilder data = new StringBuilder();
        for (String line = lines.data(); line != null; line = lines.data()) {
            data.append(line);
        }
        if (data.length() == 0) {
            throw lines.error("No Base64 data in the V2 unit");
        }
        return new V2Unit(start, lines.offset(), header.header(), characterSet, textLength, data.toString(),
                SourceLines.lineNumber(text, dataStart), SourceLines.lineBreak(text, start), keepStrayCr);
    }

    /**
     * Whether a whole line at {@code line} ends with {@code wrapped} and the marker, the character set ID and a filler
     * line follow it; the end of a line that starts before the offset belongs to a header already read.
     */
    private static boolean endsUnknownHeader(String text, int line) {
        if (line > 0 && text.charAt(line - 1) != '\n' && text.charAt(line - 1) != '\r') {
            return false;
        }
        String words = stripTrailing(text.substring(line, lineEnd(text, line)));
        int word = words.length() - WRAPPED.length();
        if (word < 0 || !words.regionMatches(true, word, WRAPPED, 0, WRAPPED.length())
                || (word > 0 && isIdentifierPart(words.charAt(word - 1)))) {
            return false;
        }
        int marker = nextLine(text, line);
        return isLine(text, marker, MARKER) && isLine(text, nextLine(text, nextLine(text, marker)), FILLER);
    }

    private static boolean isLine(String text, int line, String value) {
        return strip(text.substring(line, lineEnd(text, line))).equalsIgnoreCase(value);
    }

    /** Header of a V2 unit at {@code start} and the offset of the line after its marker, or {@code null}. */
    private static Header header(String text, int start) {
        // The header starts with a word right at the offset, not on one of the next lines
        if (start >= text.length() || !isIdentifierPart(text.charAt(start))) {
            return null;
        }
        HeaderScanner scanner = new HeaderScanner(text, start);
        String word = scanner.word();
        boolean create = "CREATE".equals(word);
        if (create) {
            word = scanner.word();
            if ("OR".equals(word)) {
                if (!"REPLACE".equals(scanner.word())) {
                    return null;
                }
                word = scanner.word();
            }
            if (word != null && EDITIONABILITY.contains(word)) {
                word = scanner.word();
            }
        }
        if (word == null || !KINDS.contains(word)) {
            return null;
        }
        String unitType = word;
        Name name = scanner.name();
        if (name != null && !name.quoted() && name.value().equals("BODY") && KINDS_WITH_BODY.contains(unitType)) {
            unitType += " BODY";
            name = scanner.name();
        }
        // WRAP 23.26.1 copies IF NOT EXISTS between the kind and the name into the header; a unit may be named IF
        if (name != null && !name.quoted() && name.value().equals("IF") && scanner.words(NOT_EXISTS)) {
            name = scanner.name();
        }
        if (name == null) {
            return null;
        }
        String unitName = name.value();
        if (scanner.symbol('.')) {
            name = scanner.name();
            if (name == null) {
                return null;
            }
            unitName += "." + name.value();
        }
        int nameEnd = name.end();
        int copyEnd = nameEnd;
        int clausesStart = -1;
        word = scanner.word();
        // WRAP 12.1 and later copy a SHARING clause right after the name into the header and leave it out of the text
        if (SHARING.equals(word)) {
            if (!scanner.symbol('=') || scanner.word() == null) {
                return null;
            }
            copyEnd = scanner.offset;
            word = scanner.word();
        }
        // WRAP copies the TIMESTAMP clause of every kind and the OID clause of a type into the header and leaves them
        // out of the text; WRAP 10.1 does so with the OID clause of every kind
        for (String clause : CLAUSES) {
            if (clause.equals(word)) {
                clausesStart = clausesStart < 0 ? scanner.offset - clause.length() : clausesStart;
                copyEnd = clause.equals(TIMESTAMP) ? scanner.timestamp() : scanner.literal();
                word = copyEnd < 0 ? null : scanner.word();
            }
        }
        if (!WRAPPED.equals(word)) {
            return null;
        }
        String clauseEnd = clausesStart < 0 ? "" : clauseEnd(text.substring(copyEnd, scanner.offset - word.length()));
        int lineEnd = lineEnd(text, scanner.offset);
        int marker = nextLine(text, scanner.offset);
        if (!strip(text.substring(scanner.offset, lineEnd)).isEmpty() || marker >= text.length()
                || !isLine(text, marker, MARKER)) {
            return null;
        }
        // WRAP copies the blanks before the header too, unless the unit follows another wrapped unit
        int indent = start;
        while (indent > 0 && (text.charAt(indent - 1) == ' ' || text.charAt(indent - 1) == '\t')) {
            indent--;
        }
        int clausesLength = clauseEnd.isEmpty() ? 0
                : lengthWithoutComments(text, nameEnd, clausesStart) + length(text, clausesStart, copyEnd) + 1;
        V2Header header = new V2Header(unitType, unitName, name.value(), text.substring(indent, copyEnd), clauseEnd,
                clausesLength, create);
        return new Header(header, nextLine(text, marker));
    }

    /**
     * The character WRAP 10.1 copies after the last clause, LF for its end of a line, or an empty string in a header of
     * WRAP 10.2 and later, which has only the space of {@code " wrapped "} there.
     *
     * @param gap the text between the last clause and {@code wrapped}
     */
    private static String clauseEnd(String gap) {
        String end = gap.endsWith(" ") ? gap.substring(0, gap.length() - 1) : "";
        if (!CLAUSE_ENDS.contains(end)) {
            return "";
        }
        return end.equals(String.valueOf(LINE_END)) ? "\n" : end;
    }

    /** Length of a part of the header in the text of the unit: WRAP has no CR there, so a CRLF stands for its LF. */
    private static int length(String text, int from, int to) {
        int length = 0;
        for (int i = from; i < to; i++) {
            if (!text.startsWith("\r\n", i)) {
                length++;
            }
        }
        return length;
    }

    /** {@link #length} of the blanks and comments between the offsets without the comments, which WRAP removes. */
    private static int lengthWithoutComments(String text, int from, int to) {
        int length = 0;
        int offset = from;
        while (offset < to) {
            if (text.startsWith("/*", offset)) {
                offset = text.indexOf("*/", offset + 2) + 2;
            } else if (text.startsWith("--", offset)) {
                offset = lineEnd(text, offset);
            } else {
                length += length(text, offset, offset + 1);
                offset++;
            }
        }
        return length;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }

    /** Offset of the line break that ends the line holding {@code offset}, or the text length. */
    private static int lineEnd(String text, int offset) {
        int end = offset;
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    /** Offset of the line after the one holding {@code offset}, or the text length. */
    private static int nextLine(String text, int offset) {
        int end = lineEnd(text, offset);
        if (end < text.length() && text.charAt(end) == '\r') {
            end++;
        }
        return end < text.length() && text.charAt(end) == '\n' ? end + 1 : end;
    }

    private static final class Header {
        private final V2Header header;
        private final int dataStart;

        Header(V2Header header, int dataStart) {
            this.header = header;
            this.dataStart = dataStart;
        }

        V2Header header() {
            return header;
        }

        int dataStart() {
            return dataStart;
        }
    }

    /**
     * Name or part of a name in the header.
     */
    private static final class Name {
        private final String value;
        private final boolean quoted;
        private final int end;

        /**
         * @param value  a quoted identifier without its quotes, an unquoted one in upper case
         * @param quoted whether the identifier is quoted
         * @param end    offset after the identifier
         */
        Name(String value, boolean quoted, int end) {
            this.value = value;
            this.quoted = quoted;
            this.end = end;
        }

        String value() {
            return value;
        }

        boolean quoted() {
            return quoted;
        }

        int end() {
            return end;
        }
    }

    /** Tokens of the header; spaces, line breaks and comments between them are skipped. */
    private static final class HeaderScanner {
        private final String text;
        private int offset;

        private HeaderScanner(String text, int offset) {
            this.text = text;
            this.offset = offset;
        }

        /** Next unquoted word in upper case, or {@code null} if the next token is not a word. */
        private String word() {
            skip();
            int start = offset;
            while (offset < text.length() && isIdentifierPart(text.charAt(offset))) {
                offset++;
            }
            return start == offset ? null : text.substring(start, offset).toUpperCase(Locale.ROOT);
        }

        /** Next identifier, quoted or not, or {@code null} if the next token is not an identifier. */
        private Name name() {
            skip();
            if (offset < text.length() && text.charAt(offset) == '"') {
                int close = text.indexOf('"', offset + 1);
                if (close < 0) {
                    return null;
                }
                String value = text.substring(offset + 1, close);
                offset = close + 1;
                return new Name(value, true, offset);
            }
            String word = word();
            return word == null ? null : new Name(word, false, offset);
        }

        /** Offset after the next token if it is a string literal without quotes inside, or -1. */
        private int literal() {
            skip();
            if (offset < text.length() && text.charAt(offset) == '\'') {
                int close = text.indexOf('\'', offset + 1);
                offset = close < 0 ? text.length() : close + 1;
                return close < 0 ? -1 : offset;
            }
            return -1;
        }

        /** Offset after the value of TIMESTAMP, a string literal or {@code OLD}, or -1. */
        private int timestamp() {
            return literal() >= 0 || "OLD".equals(word()) ? offset : -1;
        }

        /** Whether the next tokens are the unquoted {@code words}; they are taken only if they all are. */
        private boolean words(List<String> words) {
            int start = offset;
            for (String expected : words) {
                if (!expected.equals(word())) {
                    offset = start;
                    return false;
                }
            }
            return true;
        }

        /** Whether the next token is the character {@code c}; if it is, it is taken. */
        private boolean symbol(char c) {
            skip();
            if (offset < text.length() && text.charAt(offset) == c) {
                offset++;
                return true;
            }
            return false;
        }

        /**
         * Skips spaces, line breaks, the end of a line written by WRAP 10.1 and comments; an unterminated comment runs
         * to the end of the text.
         */
        private void skip() {
            while (offset < text.length()) {
                if (Character.isWhitespace(text.charAt(offset)) || text.charAt(offset) == LINE_END) {
                    offset++;
                } else if (text.startsWith("/*", offset)) {
                    int end = text.indexOf("*/", offset + 2);
                    offset = end < 0 ? text.length() : end + 2;
                } else if (text.startsWith("--", offset)) {
                    offset = lineEnd(text, offset);
                } else {
                    return;
                }
            }
        }
    }

    /** Lines of the unit after the header; an error names the line it is found on. */
    private static final class Lines {
        private final String text;
        private int offset;
        private int lineStart;

        private Lines(String text, int offset) {
            this.text = text;
            this.offset = offset;
            this.lineStart = offset;
        }

        private int offset() {
            return offset;
        }

        /** Next line without the blanks around it. */
        private String next(String what) {
            lineStart = offset;
            if (offset >= text.length()) {
                throw error("Unexpected end of the V2 unit: no " + what);
            }
            offset = nextLine(text, offset);
            return strip(text.substring(lineStart, lineEnd(text, lineStart)));
        }

        /**
         * Next line of Base64 data without the blanks around it, or {@code null} at a blank line, a {@code /} line, a
         * line with other characters or the end of the text; such a line is not taken. The lines may have any length:
         * the tools that handle a script split and join them.
         */
        private String data() {
            lineStart = offset;
            if (offset >= text.length()) {
                return null;
            }
            String line = strip(text.substring(offset, lineEnd(text, offset)));
            if (line.isEmpty() || line.equals("/") || !line.chars().allMatch(Lines::isBase64)) {
                return null;
            }
            offset = nextLine(text, offset);
            return line;
        }

        private int hex(String value, String what) {
            if (value.isEmpty() || value.length() > MAX_HEX_DIGITS || !value.chars().allMatch(Lines::isHexDigit)) {
                throw error("Invalid hexadecimal " + what + " of the V2 unit: " + value);
            }
            return Integer.parseInt(value, 16);
        }

        private UnwrapException error(String message) {
            return new UnwrapException(message + " (line " + SourceLines.lineNumber(text, lineStart) + ")");
        }

        private static boolean isBase64(int c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=';
        }

        private static boolean isHexDigit(int c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }
    }
}
