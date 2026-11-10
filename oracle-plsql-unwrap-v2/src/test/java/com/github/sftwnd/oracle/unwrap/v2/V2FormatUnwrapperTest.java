// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.v2;

import com.github.sftwnd.oracle.unwrap.spi.FormatUnwrapper;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapException;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapOptions;
import com.github.sftwnd.oracle.unwrap.spi.WrappedUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class V2FormatUnwrapperTest {

    private static final String BODY = "PACKAGE BODY pkg IS\n  X NUMBER;\nEND;";

    private final V2FormatUnwrapper format = new V2FormatUnwrapper();

    /**
     * Headers as WRAP copies them, as {@code DBMS_METADATA} writes them (with the editionability and quoted names) and
     * as the data dictionary keeps them (without CREATE); spaces, line breaks and comments may separate the words.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "CREATE OR REPLACE PACKAGE BODY pkg                        | PACKAGE BODY | PKG",
        "create package body App.pkg                               | PACKAGE BODY | APP.PKG",
        "CREATE OR REPLACE EDITIONABLE PACKAGE BODY \"APP\".\"PKG\" | PACKAGE BODY | APP.PKG",
        "create or replace noneditionable package body \"Pkg\"     | PACKAGE BODY | Pkg",
        "package body pkg                                          | PACKAGE BODY | PKG",
        "create or replace\\npackage /* c */ body -- c\\n  pkg      | PACKAGE BODY | PKG",
        "create assembly App.asm                                   | ASSEMBLY     | APP.ASM",
        "create package body if not exists pkg                     | PACKAGE BODY | PKG",
        "create package body If /* c */ Not\\n Exists \"If\"         | PACKAGE BODY | If",
        "create package body if not exists if                      | PACKAGE BODY | IF",
        "create package body if                                    | PACKAGE BODY | IF"
    })
    void findsHeaderAndReadsTypeAndName(String header, String unitType, String unitName) {
        String text = "-- before\n" + V2Units.unit(header.replace("\\n", "\n"), 1, "b", BODY, StandardCharsets.US_ASCII);

        assertEquals(10, format.find(text, 0));
        WrappedUnit unit = format.read(text, 10);
        assertEquals(unitType, unit.unitType());
        assertEquals(unitName, unit.unitName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE OR REPLACE PACKAGE BODY pkg wrapped\n0\nabcd\n",
        "CREATE OR REPLACE PACKAGE BODY pkg wrapped\n",
        "CREATE OR REPLACE PACKAGE BODY pkg wrapped x\na000000\n",
        "CREATE OR REPLACE TRIGGER trg wrapped\na000000\n",
        "CREATE OR REPLACE PACKAGE BODY wrapped\na000000\n",
        "CREATE OR REPLACE PACKAGE BODY IF NOT EXISTS wrapped\na000000\n",
        "CREATE OR REPLACE PACKAGE BODY IF NOT pkg wrapped\na000000\n",
        "CREATE OR REPLACE TYPE t OID '8188673EFEFB3346E0340800208357EE wrapped\na000000\n",
        "CREATE OR REPLACE TYPE t TIMESTAMP wrapped\na000000\n",
        "CREATE OR REPLACE TYPE t TIMESTAMP NEW wrapped\na000000\n",
        "CREATE OR REPLACE TYPE t TIMESTAMP '2001-07-29:12:06:07 wrapped\na000000\n",
        "CREATE OR REPLACE TYPE t TIMESTAMP '2001-07-29:12:06:07' TIMESTAMP '2001-07-29:12:06:07' wrapped\na000000\n",
        "CREATE OR REPLACE TYPE t OID '8188673EFEFB3346E0340800208357EE' TIMESTAMP '2001-07-29:12:06:07' wrapped\n"
                + "a000000\n",
        "CREATE OR REPLACE TYPE t OID '8188673EFEFB3346E0340800208357EE' OID '8188673EFEFB3346E0340800208357EE' "
                + "wrapped\na000000\n",
        "CREATE TABLE t (x NUMBER);\na000000\n",
        "-- CREATE OR REPLACE PACKAGE BODY pkg wrapped\na000000\n",
        "\nwrapped\na000000\n",
        "text wrapped\na000000\n1\n",
        "text wrapped\na000000\n1\nother\n",
        "text wrapped\n0\n1\nabcd\n",
        "unwrapped\na000000\n1\nabcd\n"
    })
    void ignoresOtherTexts(String text) {
        assertEquals(-1, format.find(text, 0));
    }

    @Test
    void findsUnitsFromOffset() {
        String unit = V2Units.unit("CREATE PROCEDURE p", 1, "7", "PROCEDURE p IS BEGIN NULL; END;",
                StandardCharsets.US_ASCII);
        String text = unit + unit;

        assertEquals(unit.length(), format.find(text, 1));
        assertEquals(-1, format.find(text, unit.length() + 1));
    }

    @Test
    void restoresHeaderAndTextAfterKindAndName() {
        String text = V2Units.unit("create or replace package body App . pkg", 1, "b", BODY,
                StandardCharsets.US_ASCII);

        assertEquals("create or replace package body App . pkg IS\n  X NUMBER;\nEND;\n/\n",
                format.read(text, 0).unwrap());
    }

    @Test
    void restoresHeaderWithEditionabilityAsWritten() {
        String text = V2Units.unit("CREATE OR REPLACE EDITIONABLE PACKAGE BODY \"APP\".\"PKG\"", 1, "b", BODY,
                StandardCharsets.US_ASCII);

        assertEquals("CREATE OR REPLACE EDITIONABLE PACKAGE BODY \"APP\".\"PKG\" IS\n  X NUMBER;\nEND;\n/\n",
                format.read(text, 0).unwrap());
    }

    /** WRAP copies the OID clause of a type into the header and leaves it out of the text. */
    @Test
    void restoresOidClauseOfTypeFromHeader() {
        String header = "create or replace type t\nOID '8188673EFEFB3346E0340800208357EE'";
        String text = V2Units.unit(header, 1, "d", "TYPE t\n AS OBJECT (A NUMBER);", StandardCharsets.US_ASCII);

        WrappedUnit unit = format.read(text, 0);

        assertEquals("T", unit.unitName());
        assertEquals(header + "\n AS OBJECT (A NUMBER);\n/\n", unit.unwrap());
    }

    /** WRAP 12.1 and later copy a SHARING clause right after the name into the header, as written. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "create or replace package body pkg sharing=none                          | PKG",
        "create or replace package  body pkg sharing = NONE                       | PKG",
        "create or replace package body app.pkg SHARING=METADATA                  | APP.PKG",
        "create or replace package body pkg /* a */ sharing /* b */ = /* c */ none | PKG",
        "create or replace package body pkg\\n  sharing\\n  =\\n  none             | PKG"
    })
    void restoresSharingClauseFromHeader(String clause, String unitName) {
        String header = clause.replace("\\n", "\n");
        String text = V2Units.unit(header, 1, "b", BODY, StandardCharsets.US_ASCII);

        assertEquals(0, format.find(text, 0));
        WrappedUnit unit = format.read(text, 0);

        assertEquals(unitName, unit.unitName());
        assertEquals(header + " IS\n  X NUMBER;\nEND;\n/\n", unit.unwrap());
    }

    @Test
    void restoresSharingClauseBeforeOidOfType() {
        String header = "create or replace type t sharing=none oid '8188673EFEFB3346E0340800208357EE'";
        String text = V2Units.unit(header, 1, "d", "TYPE t AS OBJECT (A NUMBER);", StandardCharsets.US_ASCII);

        assertEquals(header + " AS OBJECT (A NUMBER);\n/\n", format.read(text, 0).unwrap());
    }

    /**
     * WRAP copies the TIMESTAMP clause of every kind of unit into the header, with a string literal or {@code OLD}, and
     * WRAP 10.1 the OID clause too.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "create or replace procedure app.p timestamp '2000-01-01:00:00:00' | 7 | PROCEDURE p | \" IS NULL; END;\"",
        "create function f TIMESTAMP\\n  '2000-01-01:00:00:00' | 8 | FUNCTION f | \" RETURN NUMBER IS\"",
        "create package p timestamp old | 9 | PACKAGE p | \" IS X NUMBER; END;\"",
        "create package body p timestamp '2000-01-01:00:00:00' oid 'A' | b | PACKAGE BODY p | \" IS END;\"",
        "create type body t /* c */ timestamp old | e | TYPE BODY t | \" IS END;\"",
        "create library l oid '8188673EFEFB3346E0340800208357EE' | 16 | LIBRARY l | \" AS '/tmp/x.so';\"",
        "create procedure p sharing = none timestamp old | 7 | PROCEDURE p | \" IS NULL; END;\"",
        "create type t timestamp old oid 'A' | d | TYPE t | \" AS OBJECT (A NUMBER);\""
    })
    void restoresTimestampAndOidClausesOfEveryKindFromHeader(String header, String kind, String kindAndName,
            String afterName) {
        header = header.replace("\\n", "\n");
        String text = V2Units.unit(header, 1, kind, kindAndName + afterName, StandardCharsets.US_ASCII);

        assertEquals(0, format.find(text, 0));
        assertEquals(header + afterName + "\n/\n", format.read(text, 0).unwrap());
    }

    /** WRAP 23.26.1 copies {@code IF NOT EXISTS} between the kind and the name into the header. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "create procedure if not exists p | 7 | PROCEDURE p | \" IS NULL; END;\" | PROCEDURE | P",
        "create editionable package body If /* c */ Not\\n Exists app.p sharing = none | b | PACKAGE BODY p "
                + "| \" IS END;\" | PACKAGE BODY | APP.P",
        "create type if not exists t timestamp old | d | TYPE t | \" AS OBJECT (A NUMBER);\" | TYPE | T",
        "create procedure if not exists if | 7 | PROCEDURE if | \" IS NULL; END;\" | PROCEDURE | IF",
        "create procedure if | 7 | PROCEDURE if | \" NOT EXISTS P IS NULL; END;\" | PROCEDURE | IF"
    })
    void restoresIfNotExistsFromHeader(String header, String kind, String kindAndName, String afterName,
            String unitType, String unitName) {
        header = header.replace("\\n", "\n");
        String text = V2Units.unit(header, 1, kind, kindAndName + afterName, StandardCharsets.US_ASCII);

        WrappedUnit unit = format.read(text, 0);

        assertEquals(unitType, unit.unitType());
        assertEquals(unitName, unit.unitName());
        assertEquals(header + afterName + "\n/\n", unit.unwrap());
    }

    /** WRAP 11.1 and later wrap a unit of the kind {@code ASSEMBLY}, the object type 87 of the data dictionary. */
    @Test
    void restoresAssembly() {
        String text = V2Units.unit("create or replace assembly App.asm", 1, "57", "ASSEMBLY asm AS 'c:\\x.dll';",
                StandardCharsets.US_ASCII);

        WrappedUnit unit = format.read(text, 0);

        assertEquals("ASSEMBLY", unit.unitType());
        assertEquals("APP.ASM", unit.unitName());
        assertEquals("create or replace assembly App.asm AS 'c:\\x.dll';\n/\n", unit.unwrap());
    }

    /** WRAP copies the TIMESTAMP clause of a type, alone or before the OID clause, into the header as well. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "create type t timestamp '2001-07-29:12:06:07'                                       | T     | "
                + "create type t timestamp '2001-07-29:12:06:07'",
        "create type t timestamp '2001-07-29:12:06:07' oid '8188673EFEFB3346E0340800208357EE' | T     | "
                + "create type t timestamp '2001-07-29:12:06:07' oid '8188673EFEFB3346E0340800208357EE'",
        "create or replace type t\\n TIMESTAMP '2001-07-29:12:06:07'\\n "
                + "OID '8188673EFEFB3346E0340800208357EE' | T | create or replace type t\\n "
                + "TIMESTAMP '2001-07-29:12:06:07'\\n OID '8188673EFEFB3346E0340800208357EE'",
        "type App.t /* c */ Timestamp\\n'2001-07-29:12:06:07'\\nOid\\n'8188673EFEFB3346E0340800208357EE' | APP.T | "
                + "CREATE OR REPLACE type App.t /* c */ Timestamp\\n'2001-07-29:12:06:07'\\nOid\\n"
                + "'8188673EFEFB3346E0340800208357EE'"
    })
    void restoresTimestampAndOidClausesOfTypeFromHeader(String header, String unitName, String restored) {
        header = header.replace("\\n", "\n");
        String text = V2Units.unit(header, 1, "d", "TYPE t\n AS OBJECT (A NUMBER);", StandardCharsets.US_ASCII);

        WrappedUnit unit = format.read(text, 0);

        assertEquals("TYPE", unit.unitType());
        assertEquals(unitName, unit.unitName());
        assertEquals(restored.replace("\\n", "\n") + "\n AS OBJECT (A NUMBER);\n/\n", unit.unwrap());
    }

    /**
     * WRAP 10.1 copies the character after the last clause into the header and replaces the clauses and that character
     * with spaces in the text; what stays between the name and the clauses is the header without its comments.
     */
    @ParameterizedTest
    @MethodSource("headersWithClausesReplacedBySpaces")
    void restoresClausesInPlaceOfSpaces(String header, String textAfterName, String restored) {
        String text = V2Units.unit(header, 1, "d", "TYPE T" + textAfterName, StandardCharsets.US_ASCII);

        assertEquals(restored, format.read(text, 0).unwrap());
    }

    private static Stream<Arguments> headersWithClausesReplacedBySpaces() {
        return Stream.of(
                arguments("create type t\nOID 'A'\n", "\n" + spaces(8) + "AS OBJECT;",
                        "create type t\nOID 'A'\nAS OBJECT;\n/\n"),
                arguments("create type t OID 'A'\t", spaces(9) + "AS OBJECT;",
                        "create type t OID 'A'\tAS OBJECT;\n/\n"),
                arguments("create type t OID 'A' ", spaces(9) + "AS OBJECT;",
                        "create type t OID 'A' AS OBJECT;\n/\n"),
                arguments("create type t OID 'A'\n", spaces(12) + "AS OBJECT;",
                        "create type t OID 'A'\n   AS OBJECT;\n/\n"),
                arguments("create type t\nOID 'A'\n", "\n" + spaces(8) + "\n\n  AS OBJECT;",
                        "create type t\nOID 'A'\n\n\n  AS OBJECT;\n/\n"),
                arguments("create type t /* c */ OID 'A'\n", spaces(10) + "AS OBJECT;",
                        "create type t /* c */ OID 'A'\nAS OBJECT;\n/\n"),
                arguments("create type t -- c\n OID 'A'\n", " \n" + spaces(9) + "AS OBJECT;",
                        "create type t -- c\n OID 'A'\nAS OBJECT;\n/\n"),
                arguments("create type t TIMESTAMP /* c */ 'B' OID 'A'\n", spaces(31) + "AS OBJECT;",
                        "create type t TIMESTAMP /* c */ 'B' OID 'A'\nAS OBJECT;\n/\n"),
                arguments("create type t\r\nOID 'A'\r\n", "\n" + spaces(8) + "AS OBJECT;",
                        "create type t\r\nOID 'A'\r\nAS OBJECT;\r\n/\r\n"));
    }

    /** WRAP 10.1 does the same with the TIMESTAMP and OID clauses of every kind of unit. */
    @ParameterizedTest
    @MethodSource("otherKindsWithClausesReplacedBySpaces")
    void restoresClausesOfOtherKindsInPlaceOfSpaces(String header, String kind, String text, String restored) {
        assertEquals(restored, format.read(V2Units.unit(header, 1, kind, text, StandardCharsets.US_ASCII), 0).unwrap());
    }

    private static Stream<Arguments> otherKindsWithClausesReplacedBySpaces() {
        return Stream.of(
                arguments("create procedure p TIMESTAMP OLD ", "7", "PROCEDURE P" + spaces(15) + "IS BEGIN NULL; END;",
                        "create procedure p TIMESTAMP OLD IS BEGIN NULL; END;\n/\n"),
                arguments("create procedure p OID 'A' ", "7", "PROCEDURE P" + spaces(9) + " IS BEGIN NULL; END;",
                        "create procedure p OID 'A'  IS BEGIN NULL; END;\n/\n"),
                arguments("create package p TIMESTAMP 'B' OID 'A'\n", "9", "PACKAGE P" + spaces(23) + "IS END;",
                        "create package p TIMESTAMP 'B' OID 'A'\nIS END;\n/\n"),
                arguments("create library l\nOID 'A'\t", "16", "LIBRARY L\n" + spaces(8) + "AS 'x';",
                        "create library l\nOID 'A'\tAS 'x';\n/\n"),
                // A clause at the end of its line is followed by NUL in the header, which stands for the line break
                arguments("create function f\n  timestamp 'B'\0", "8", "FUNCTION F\n  " + spaces(14) + "RETURN NUMBER IS",
                        "create function f\n  timestamp 'B'\nRETURN NUMBER IS\n/\n"));
    }

    private static String spaces(int count) {
        return String.join("", Collections.nCopies(count, " "));
    }

    /** Fewer blanks in the text than the clauses take are not the spaces of WRAP 10.1, so the text stays as it is. */
    @Test
    void keepsTextWithoutSpacesInPlaceOfClauses() {
        String text = V2Units.unit("create type t OID 'A'\n", 1, "d", "TYPE T\n AS OBJECT;", StandardCharsets.US_ASCII);

        assertEquals("create type t OID 'A'\n AS OBJECT;\n/\n", format.read(text, 0).unwrap());
    }

    /**
     * A text that ends with a blank or a line break ends where WRAP removed a comment, or where WRAP 10.1 found the
     * {@code /} of the source: an empty block comment after it keeps the text, because WRAP removes such a comment
     * together with the line break after it.
     */
    @ParameterizedTest
    @MethodSource("endsOfText")
    void marksTextThatEndsWithBlank(String end, String restoredEnd) {
        String unit = V2Units.unit("create package body pkg", 1, "b", "PACKAGE BODY pkg IS\n" + end,
                StandardCharsets.US_ASCII);

        assertEquals("create package body pkg IS\n" + restoredEnd, format.read(unit, 0).unwrap());
    }

    private static Stream<Arguments> endsOfText() {
        return Stream.of(
                arguments("END;", "END;\n/\n"),
                arguments("END;   ", "END;   /**/\n/\n"),
                arguments("END;\n", "END;\n/**/\n/\n"),
                arguments("END;\n\n", "END;\n\n/**/\n/\n"),
                arguments("END;  \n", "END;  \n/**/\n/\n"),
                arguments("END;\n  ", "END;\n  /**/\n/\n"),
                arguments("END;\n\t", "END;\n\t/**/\n/\n"));
    }

    @Test
    void marksTextThatEndsWithBlankWithLineBreakOfHeader() {
        String unit = V2Units.unit("create package body pkg", 1, "b", "PACKAGE BODY pkg IS\nEND;\n  ",
                StandardCharsets.US_ASCII).replace("\n", "\r\n");

        assertEquals("create package body pkg IS\r\nEND;\r\n  /**/\r\n/\r\n", format.read(unit, 0).unwrap());
    }

    /** WRAP copies the blanks before the header of a unit that does not follow another wrapped unit. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "create procedure p | create procedure p",
        "procedure p        | CREATE OR REPLACE procedure p"
    })
    void keepsBlanksBeforeHeader(String header, String restored) {
        String text = "GRANT EXECUTE ON x TO PUBLIC;\n \t" + V2Units.unit(header, 1, "7",
                "PROCEDURE p IS BEGIN NULL; END;", StandardCharsets.US_ASCII);

        assertEquals(32, format.find(text, 0));
        assertEquals(" \t" + restored + " IS BEGIN NULL; END;\n/\n", format.read(text, 32).unwrap());
    }

    /** Text from the data dictionary has no CREATE: the statement gets {@code CREATE OR REPLACE}. */
    @Test
    void restoresHeaderWithoutCreateAsCreateOrReplace() {
        String text = V2Units.unit("package body pkg", 1, "b", BODY, StandardCharsets.US_ASCII);

        assertEquals("CREATE OR REPLACE package body pkg IS\n  X NUMBER;\nEND;\n/\n", format.read(text, 0).unwrap());
    }

    /** WRAP removes a comment between the name and {@code AS} without a separator; a space is written instead. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "create or replace type body Obj   | TYPE BODY ObjAS MEMBER PROCEDURE M IS BEGIN NULL; END; END;",
        "create or replace type body \"OBJ\" | TYPE BODY objAS MEMBER PROCEDURE M IS BEGIN NULL; END; END;"
    })
    void separatesNameGluedWithNextWord(String header, String body) {
        String text = V2Units.unit(header, 1, "e", body, StandardCharsets.US_ASCII);

        assertEquals(header + " AS MEMBER PROCEDURE M IS BEGIN NULL; END; END;\n/\n", format.read(text, 0).unwrap());
    }

    @Test
    void restoresQuotedNameOfText() {
        String text = V2Units.unit("create procedure \"My Proc\"", 1, "7", "PROCEDURE \"My Proc\"(A NUMBER) IS BEGIN"
                + " NULL; END;", StandardCharsets.US_ASCII);

        assertEquals("create procedure \"My Proc\"(A NUMBER) IS BEGIN NULL; END;\n/\n", format.read(text, 0).unwrap());
    }

    /** In a unit with CRLF, CRLF and LF of the text are written as CRLF; a lone CR is part of a literal. */
    @Test
    void writesLineBreaksOfTextWithLineBreakOfHeader() {
        String body = "PROCEDURE p IS\r\n  V VARCHAR2(9) := 'a\nb\rc';\nBEGIN NULL; END;";
        String text = V2Units.unit("create procedure p", 1, "7", body, StandardCharsets.US_ASCII)
                .replace("\n", "\r\n");

        assertEquals("create procedure p IS\r\n  V VARCHAR2(9) := 'a\r\nb\rc';\r\nBEGIN NULL; END;\r\n/\r\n",
                format.read(text, 0).unwrap());
    }

    /**
     * WRAP 12.1 and later keep the CR of the source and write the wrapped lines with LF: in a unit with LF a CRLF is
     * part of the text, in a literal too, and stays next to the LF of other lines.
     */
    @Test
    void keepsCrlfOfTextInUnitWithLf() {
        String body = "PROCEDURE p IS\r\n  V VARCHAR2(9) := 'a\r\nb\rc';\nBEGIN NULL; END;";
        String text = V2Units.unit("create procedure p", 1, "7", body, StandardCharsets.US_ASCII);

        assertEquals("create procedure p IS\r\n  V VARCHAR2(9) := 'a\r\nb\rc';\nBEGIN NULL; END;\n/\n",
                format.read(text, 0).unwrap());
    }

    /**
     * A stray CR of the text, as WRAP 10.2 to 11.2 leave it in the last byte of a block of the source, becomes a space
     * by default and stays with the option {@code strayCr=keep}; the text of a unit with more CRs than blanks before
     * its line breaks is not touched.
     */
    @Test
    void replacesStrayCrUnlessTheOptionKeepsIt() {
        String body = "PROCEDURE p IS \n  A NUMBER; \n  B NUMBER;\r\n  C NUMBER; \nBEGIN NULL; END;";
        String text = V2Units.unit("create procedure p", 1, "7", body, StandardCharsets.US_ASCII);
        String restored = "create procedure p IS \n  A NUMBER; \n  B NUMBER;%s\n  C NUMBER; \nBEGIN NULL; END;\n/\n";

        assertEquals(String.format(restored, " "), format.read(text, 0).unwrap());
        assertEquals(String.format(restored, " "), withStrayCr("space").read(text, 0).unwrap());
        assertEquals(String.format(restored, "\r"), withStrayCr("keep").read(text, 0).unwrap());
        assertEquals(String.format(restored, "\r"), withStrayCr(" KEEP ").read(text, 0).unwrap());
    }

    @Test
    void listsTheOptionStrayCr() {
        assertEquals(1, format.options().size());
        assertEquals("strayCr", format.options().get(0).name());
        assertEquals(Arrays.asList("space", "keep"), format.options().get(0).values());
    }

    @Test
    void rejectsWrongValueOfTheOption() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> withStrayCr("yes"));
        assertEquals("Option V2.strayCr must be space or keep, not yes", e.getMessage());
    }

    @Test
    void ignoresOptionsOfOtherFormats() {
        assertEquals(V2FormatUnwrapper.class,
                format.withOptions(UnwrapOptions.defaults().withFormatOption("V1", "strayCr", "yes")).getClass());
    }

    private static FormatUnwrapper withStrayCr(String value) {
        return new V2FormatUnwrapper().withOptions(UnwrapOptions.defaults().withFormatOption("V2", "strayCr", value));
    }

    @Test
    void writesLineBreaksOfTextWithCrOfHeader() {
        String body = "PROCEDURE p IS\r\n  V NUMBER;\nBEGIN NULL; END;";
        String text = V2Units.unit("create procedure p", 1, "7", body, StandardCharsets.US_ASCII)
                .replace("\n", "\r");

        assertEquals("create procedure p IS\r  V NUMBER;\rBEGIN NULL; END;\r/\r", format.read(text, 0).unwrap());
    }

    /**
     * Tools that handle a script may split or join the Base64 lines, add blanks and drop the empty line before the
     * {@code /} line; the data is read up to that line.
     */
    @Test
    void readsBase64LinesSplitDifferently() {
        String unit = V2Units.unit("create procedure p", 1, "7", "PROCEDURE p IS BEGIN NULL; END;",
                StandardCharsets.US_ASCII);
        int data = unit.indexOf('\n', unit.indexOf("abcd\n7\n") + 7) + 1;
        String base64 = unit.substring(data, unit.indexOf("\n\n/")).replace("\n", "");
        String text = unit.substring(0, data) + base64.substring(0, 10) + "  \n\t" + base64.substring(10) + "\n/\nX";

        WrappedUnit wrapped = format.read(text, 0);

        assertEquals("create procedure p IS BEGIN NULL; END;\n/\n", wrapped.unwrap());
        assertEquals(text.length() - 3, wrapped.end());
    }

    /** Without the empty line and the {@code /} line the data ends before the next header, which has blanks inside. */
    @Test
    void endsDataBeforeNextHeader() {
        String unit = V2Units.unit("create procedure p", 1, "7", "PROCEDURE p IS BEGIN NULL; END;",
                StandardCharsets.US_ASCII);
        String first = unit.substring(0, unit.length() - "\n/\n".length());
        String text = first + unit.replace("procedure p", "procedure q").replace("PROCEDURE p", "PROCEDURE q");

        WrappedUnit wrapped = format.read(text, 0);

        assertEquals(first.length(), wrapped.end());
        assertEquals(first.length(), format.find(text, wrapped.end()));
        assertEquals("create procedure p IS BEGIN NULL; END;\n/\n", wrapped.unwrap());
    }

    /** Only a unit has the lines after {@code wrapped}, so such a unit is reported and does not stay wrapped. */
    @ParameterizedTest
    @ValueSource(strings = {
        "create or replace package pkg collation using_nls_comp",
        "create or replace package pkg sharing",
        "create or replace package pkg sharing =",
        "create or replace trigger trg",
        "create or replace type t oid '8188673EFEFB3346E0340800208357EE' sharing=none",
        "create or replace procedure p timestamp new",
        "create or replace procedure p oid '8188673EFEFB3346E0340800208357EE' timestamp old",
        "create or replace procedure if not exists"
    })
    void reportsHeaderItCannotRead(String header) {
        String text = "-- before\n" + V2Units.unit(header, 1, "b", BODY, StandardCharsets.US_ASCII);

        assertEquals(10, format.find(text, 0));
        UnwrapException e = assertThrows(UnwrapException.class, () -> format.read(text, 10));
        assertEquals("Unknown V2 unit header (line 2)", e.getMessage());
    }

    @Test
    void findsHeaderItCannotReadByItsLastLine() {
        String text = V2Units.unit("create or replace package pkg\n  other", 1, "b", BODY, StandardCharsets.US_ASCII);

        int start = format.find(text, 0);

        assertEquals(text.indexOf("other"), start);
        UnwrapException e = assertThrows(UnwrapException.class, () -> format.read(text, start));
        assertEquals("Unknown V2 unit header (line 2)", e.getMessage());
    }

    @Test
    void reportsTextWithoutKindOfHeader() {
        String text = V2Units.unit("create package body pkg", 1, "b", "PACKAGE pkg IS END;", StandardCharsets.US_ASCII);

        UnwrapException e = assertThrows(UnwrapException.class, () -> format.read(text, 0).unwrap());
        assertEquals("Text of the V2 unit does not start with PACKAGE BODY (line 21)", e.getMessage());
    }

    @Test
    void reportsDamagedDataWithLineNumber() {
        String unit = V2Units.unit("create procedure p", 1, "7", "PROCEDURE p IS BEGIN NULL; END;",
                StandardCharsets.US_ASCII);
        int data = unit.indexOf('\n', unit.indexOf("abcd\n7\n") + 7) + 1;
        char[] text = ("\n" + unit).toCharArray();
        text[data + 11] = text[data + 11] == 'A' ? 'B' : 'A';

        UnwrapException e = assertThrows(UnwrapException.class, () -> format.read(new String(text), 1).unwrap());
        assertTrue(e.getMessage().startsWith("SHA-1 of the V2 unit data does not match"), e.getMessage());
        assertTrue(e.getMessage().endsWith("(line 22)"), e.getMessage());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "a000000\\nzz                       | Invalid hexadecimal character set ID of the V2 unit: zz (line 4)",
        "a000000\\n+1                       | Invalid hexadecimal character set ID of the V2 unit: +1 (line 4)",
        "a000000\\n1\\nabcd\\n              | Unexpected end of the V2 unit: no unit kind (line 6)",
        "a000000\\n1\\nabcd\\n7\\n2b          | Expected two text lengths in the V2 unit (line 7)",
        "a000000\\n1\\nabcd\\n7\\n2b 61\\n\\n/ | No Base64 data in the V2 unit (line 8)"
    })
    void reportsBrokenUnitWithLineNumber(String rest, String message) {
        String text = "\ncreate procedure p wrapped \n" + rest.replace("\\n", "\n");

        UnwrapException e = assertThrows(UnwrapException.class, () -> format.read(text, 1));
        assertEquals(message, e.getMessage());
    }
}
