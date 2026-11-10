// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap;

import com.github.sftwnd.oracle.unwrap.spi.Samples;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapException;
import com.github.sftwnd.oracle.unwrap.spi.UnwrapOptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnwrapperTest {

    /** The option of the format V2 that keeps a stray CR of the text. */
    private static final UnwrapOptions KEEP_STRAY_CR = UnwrapOptions.defaults().withFormatOption("V2", "strayCr", "keep");

    private final Unwrapper unwrapper = new Unwrapper();

    @Test
    void copiesScriptWithoutUnitsAsIs() {
        String script = "-- head\r\n-- end\r\n";

        assertEquals(script, unwrapper.unwrapScript(script));
    }

    /**
     * V2 units of every kind with the headers WRAP copies: with a schema, without {@code OR REPLACE}, with quoted names,
     * over several lines, with a comment before {@code IS}, with the OID clause of a type and with the blanks before a
     * unit that follows plain SQL; the text of a unit is normalized by WRAP. Plain SQL between the units is copied, and
     * a {@code --} line goes before the indented header that follows a {@code /} line. A synthetic script wrapped by
     * WRAP 10.2.0.5; the reference output is verified by a round trip: WRAP 10.2.0.5 gives the same wrapped file from
     * it.
     */
    @Test
    void restoresV2UnitsWithTheirHeaders() {
        assertEquals(Samples.expected("P1020HEADER.unwrapped", "P1020HEADER.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1020HEADER.wrapped")));
    }

    /**
     * The SHARING clause ({@code P1210SHARING}) in every kind of unit, in other case, with blanks and comments, over
     * several lines, before OID and in places where WRAP leaves it in the text, wrapped by WRAP 12.1.0.2; the reference
     * output is verified by a round trip: WRAP 12.1.0.2 and 21.3 give the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWithSharingClause() {
        assertEquals(Samples.expected("P1210SHARING.unwrapped", "P1210SHARING.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1210SHARING.wrapped")));
    }

    /** The text has a unit, so {@code supports} accepts it, but the unit must not stay wrapped in the script. */
    @Test
    void reportsV2UnitWithUnknownHeader() {
        String script = Samples.read("P1210SHARING.wrapped")
                .replace("sharing=metadata wrapped", "other=metadata wrapped");

        assertTrue(unwrapper.supports(script));
        assertEquals("Unknown V2 unit header (line 1)",
                assertThrows(UnwrapException.class, () -> unwrapper.unwrapScript(script)).getMessage());
        assertThrows(UnwrapException.class, () -> unwrapper.unwrapUnits(script));
        assertThrows(UnwrapException.class, () -> unwrapper.unwrap(script));
    }

    /**
     * The SHARING clause followed by the TIMESTAMP clause, with a string literal and with {@code OLD}, in a procedure, a
     * package, a function and a type with the OID clause ({@code P1210CLAUSES}). A synthetic script wrapped by WRAP
     * 12.1.0.2; the reference output is verified by a round trip: WRAP 12.1.0.2, 12.2.0.1, 21.3 and 23.26.1 give the
     * same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWithSharingAndTimestampClauses() {
        assertEquals(Samples.expected("P1210CLAUSES.unwrapped", "P1210CLAUSES.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1210CLAUSES.wrapped")));
    }

    /**
     * The TIMESTAMP clause in every kind of unit, with a string literal and with {@code OLD}, over several lines and
     * after a comment, and the OID clause of a type and of a procedure ({@code P1020CLAUSES}). WRAP copies the TIMESTAMP
     * clause of every kind and the OID clause of a type into the header, and leaves the OID clause of a procedure in the
     * text. A synthetic script wrapped by WRAP 10.2.0.5; the reference output is verified by a round trip: WRAP 10.2.0.5
     * and 11.1.0.6 give the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWithTimestampClauseOfEveryKind() {
        assertEquals(Samples.expected("P1020CLAUSES.unwrapped", "P1020CLAUSES.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1020CLAUSES.wrapped")));
    }

    /**
     * The TIMESTAMP and OID clauses in every kind of unit of WRAP 10.1 ({@code P1010CLAUSES}): WRAP copies them into the
     * header and replaces them with spaces in the text. After a clause that ends its line, a unit other than a type has
     * NUL in the header in place of the line break. A synthetic script wrapped by WRAP 10.1.0.3; the reference output is
     * verified by a round trip: WRAP 10.1.0.3 gives the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsOfWrap101WithClausesOfEveryKind() {
        assertEquals(Samples.expected("P1010CLAUSES.unwrapped", "P1010CLAUSES.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1010CLAUSES.wrapped")));
    }

    /**
     * Units of the kind ASSEMBLY ({@code P1110ASSEMBLY}): with a schema and a quoted name, over two lines and with the
     * TIMESTAMP clause. A synthetic script wrapped by WRAP 11.1.0.6; the reference output is verified by a round trip:
     * WRAP 11.1.0.6, 11.2.0.1, 12.1.0.2, 21.3 and 23.26.1 give the same wrapped file from it.
     */
    @Test
    void restoresV2Assemblies() {
        assertEquals(Samples.expected("P1110ASSEMBLY.unwrapped", "P1110ASSEMBLY.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1110ASSEMBLY.wrapped")));
    }

    /**
     * {@code IF NOT EXISTS} between the kind and the name ({@code P23261EXISTS}) in every kind of unit, with
     * EDITIONABLE, a schema, the SHARING and TIMESTAMP clauses, a comment and a line break between its words and before
     * a quoted name {@code "If"}. A synthetic script wrapped by WRAP 23.26.1; the reference output is verified by a
     * round trip: WRAP 23.26.1 gives the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWithIfNotExists() {
        assertEquals(Samples.expected("P23261EXISTS.unwrapped", "P23261EXISTS.wrapped"),
                unwrapper.unwrapScript(Samples.read("P23261EXISTS.wrapped")));
    }

    @Test
    void findsEveryV2UnitWithIfNotExists() {
        List<UnwrapResult> units = unwrapper.unwrapUnits(Samples.read("P23261EXISTS.wrapped"));

        assertEquals(Arrays.asList("PROCEDURE P_INE", "FUNCTION APP.F_INE", "PACKAGE PKG_INE", "PACKAGE BODY PKG_INE",
                        "TYPE OBJ_INE", "TYPE BODY OBJ_INE", "LIBRARY LIB_INE", "ASSEMBLY ASM_INE", "PROCEDURE If",
                        "PROCEDURE P_REPLACE"),
                units.stream().map(unit -> unit.unitType() + " " + unit.unitName()).collect(Collectors.toList()));
    }

    /**
     * Types with the TIMESTAMP and OID clauses as the data dictionary exports them ({@code P1020TYPE}): both clauses,
     * only one of them, over several lines and in other case. WRAP copies the clauses into the header and leaves them
     * out of the text. A synthetic script wrapped by WRAP 10.2.0.5; the reference output is verified by a round trip:
     * WRAP 10.2.0.5 gives the same wrapped file from it.
     */
    @Test
    void restoresV2TypesWithTimestampAndOidClauses() {
        assertEquals(Samples.expected("P1020TYPE.unwrapped", "P1020TYPE.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1020TYPE.wrapped")));
    }

    @Test
    void findsEveryV2TypeWithTimestampAndOidClauses() {
        List<UnwrapResult> units = unwrapper.unwrapUnits(Samples.read("P1020TYPE.wrapped"));

        assertEquals(Arrays.asList(
                        "TYPE APP.OBJ_BOTH", "TYPE BODY APP.OBJ_BOTH", "TYPE APP.OBJ_LINES", "TYPE Obj_Quoted",
                        "TYPE OBJ_TS_ONLY", "TYPE OBJ_OID_ONLY", "TYPE OBJ_NONE"),
                units.stream().map(unit -> unit.unitType() + " " + unit.unitName()).collect(Collectors.toList()));
    }

    /**
     * Units whose text WRAP 10.2.0.5 does not give back unchanged ({@code P1020EDGE}): a comment between the name of a
     * type body and {@code AS} is removed without a separator, and a space is written there; a unit without the
     * {@code /} line takes the next statement as its text; the text of a unit at the end of a file without the
     * {@code /} line keeps the line break after it, and an empty comment after the text keeps it in the restored
     * unit. The round trip gives the same wrapped file except for the space.
     */
    @Test
    void restoresV2UnitsWithEdgeCases() {
        assertEquals(Samples.expected("P1020EDGE.unwrapped", "P1020EDGE.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1020EDGE.wrapped")));
    }

    /**
     * WRAP 10.1 keeps the text up to the {@code /} of the source ({@code P1010TAIL}): blank lines, blanks after the
     * last token and on the last line, a {@code /} after blanks. The text ends with the last token only if the line
     * ends there and the next line is the {@code /} line. A text that ends with a blank or a line break gets an empty
     * comment before the {@code /}. A synthetic script wrapped by WRAP 10.1.0.3; the reference output is verified by a
     * round trip: WRAP 10.1.0.3 gives the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWithBlanksBeforeTerminator() {
        assertEquals(Samples.expected("P1010TAIL.unwrapped", "P1010TAIL.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1010TAIL.wrapped")));
    }

    /**
     * Types of WRAP 10.1 with the TIMESTAMP and OID clauses ({@code P1010TYPE}): WRAP copies the clauses and the next
     * character into the header and replaces them with spaces in the text, the comments among them too. The restored
     * statement has the clauses in place of the spaces, in all header forms: over several lines, on the line of the
     * name, with a tab, with comments before the clauses, among them and after them. A synthetic script wrapped by WRAP
     * 10.1.0.3; the reference output is verified by a round trip: WRAP 10.1.0.3 gives the same wrapped file from it.
     */
    @Test
    void restoresV2TypesWithClausesReplacedBySpaces() {
        assertEquals(Samples.expected("P1010TYPE.unwrapped", "P1010TYPE.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1010TYPE.wrapped")));
    }

    /** The clauses of a type are found in the text of the unit after a change of the line endings of the script. */
    @Test
    void restoresV2TypesWithClausesReplacedBySpacesWithLineBreakOfInput() {
        String script = Samples.read("P1010TYPE.wrapped").replace("\n", "\r\n");

        assertEquals(Samples.read("P1010TYPE.unwrapped"), unwrapper.unwrapScript(script));
    }

    /**
     * WRAP 10.1 replaces every CR of the source with a space, in the header and in the text ({@code P1010CRLF}), so the
     * wrapped file has no CR and the blank at the end of a line stays. A synthetic script with CRLF wrapped by WRAP
     * 10.1.0.3; the reference output is verified by a round trip: WRAP 10.1.0.3 gives the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWrappedFromCrlf() {
        assertEquals(Samples.expected("P1010CRLF.unwrapped", "P1010CRLF.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1010CRLF.wrapped")));
    }

    /**
     * WRAP 12.1 and later keep the CR of the source in the text and write the wrapped lines with LF
     * ({@code P1210CRLF}): the text keeps its CRLF, in a literal too, and a unit may mix CRLF and LF. A synthetic
     * script wrapped by WRAP 12.1.0.2; the reference output is verified by a round trip: WRAP 12.1.0.2, 12.2.0.1 and
     * 21.3 give the same wrapped file from it. The reference is stored as it is, the line ends of the text differ.
     */
    @Test
    void keepsCrlfOfV2TextWrappedFromCrlf() {
        assertEquals(Samples.read("P1210CRLF.unwrapped"), unwrapper.unwrapScript(Samples.read("P1210CRLF.wrapped")));
    }

    /** The CRs of WRAP 12.1 and later stand before the line breaks of the text, so none of them is stray. */
    @Test
    void keepsCrlfOfV2TextWrappedFromCrlfWithEitherOption() {
        String wrapped = Samples.read("P1210CRLF.wrapped");

        assertEquals(unwrapper.unwrapScript(wrapped), new Unwrapper(KEEP_STRAY_CR).unwrapScript(wrapped));
    }

    /**
     * WRAP 10.2 to 11.2 replace the CR of a source with CRLF with a space, except where it stood in the last byte of a
     * block of 512 bytes of the source ({@code P1020STRAYCR}): a synthetic script with CRLF whose CRs stand at the
     * offsets 511, 1023 and 1535, outside a literal, in a string literal and in a q-quoted literal, wrapped by WRAP
     * 10.2.0.5. By default the stray CR outside the literals becomes a space and the CRs of the literals stay; with
     * {@code strayCr=keep} the text is restored as the unit holds it. The references are stored as they are. The text
     * restored with {@code strayCr=keep} wraps back to the same file with WRAP 10.2.0.5 and 11.1.0.6 once a comment
     * before each CR moves it to the end of a block again.
     */
    @Test
    void replacesStrayCrOfV2TextWithSpace() {
        String wrapped = Samples.read("P1020STRAYCR.wrapped");

        assertEquals(Samples.read("P1020STRAYCR.unwrapped"), unwrapper.unwrapScript(wrapped));
        assertEquals(Samples.read("P1020STRAYCRKEPT.unwrapped"), new Unwrapper(KEEP_STRAY_CR).unwrapScript(wrapped));
    }

    /**
     * The rule depends on the text of the unit only: the same file with CRLF gives the same restored script with
     * CRLF, the line breaks of the literals included.
     */
    @Test
    void replacesStrayCrOfV2TextWhateverTheLineEndingsOfTheFile() {
        String wrapped = Samples.read("P1020STRAYCR.wrapped");
        String restored = unwrapper.unwrapScript(wrapped);

        assertEquals(restored.replace("\r\n", "\n").replace("\n", "\r\n"),
                unwrapper.unwrapScript(wrapped.replace("\n", "\r\n")));
    }

    /**
     * Ends of the text where WRAP 10.1 removed a comment ({@code P1010EDGE}): a comment after the last token is removed
     * with the next line break, which leaves blanks without a line break or a single line break after the last token,
     * and the text of a unit at the end of a file without the {@code /} line keeps its line break. An empty comment
     * after such a text keeps it. A synthetic script wrapped by WRAP 10.1.0.3; the reference output is verified by a
     * round trip: WRAP 10.1.0.3 gives the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsWithCommentsAtTheEnd() {
        assertEquals(Samples.expected("P1010EDGE.unwrapped", "P1010EDGE.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1010EDGE.wrapped")));
    }

    /**
     * Traces of removed comments ({@code P1020COMMENT}): a comment after the last token leaves the blanks and blank
     * lines before it in the text, and a comment line right after a {@code /} line leaves the blanks after it before
     * plain SQL and before the header of the next unit. The restored script has an empty block comment after such a
     * text and a {@code --} line before such blanks, also where WRAP keeps the blanks anyway, after the {@code /} of an
     * SQL statement. A synthetic script wrapped by WRAP 10.2.0.5, which WRAP 11.1.0.6 wraps to the same file; the
     * reference output is verified by a round trip with both releases.
     */
    @Test
    void restoresV2UnitsWithTracesOfComments() {
        assertEquals(Samples.expected("P1020COMMENT.unwrapped", "P1020COMMENT.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1020COMMENT.wrapped")));
    }

    /**
     * The script of {@code P1020COMMENT} wrapped by WRAP 10.1.0.3 ({@code P1010COMMENT}): the text keeps the line break
     * after a {@code --} comment too, the blanks before plain SQL stay with or without a comment line, and the header
     * of a unit loses its blanks in any case. The same marks are written; the reference output is verified by a round
     * trip: WRAP 10.1.0.3 gives the same wrapped file from it.
     */
    @Test
    void restoresV2UnitsOfWrap101WithTracesOfComments() {
        assertEquals(Samples.expected("P1010COMMENT.unwrapped", "P1010COMMENT.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1010COMMENT.wrapped")));
    }

    /**
     * A source that ends with {@code /} without a line break ({@code P1020EOF}): WRAP 10.2 and later end the wrapped
     * file the same way, and the restored script ends with the {@code /} of the last unit without a line break; the
     * {@code /} of the first unit keeps its line break. A synthetic script wrapped by WRAP 10.2.0.5, which WRAP 11.1.0.6
     * and 11.2.0.1 wrap to the same file; the reference output is verified by a round trip with all three releases.
     */
    @Test
    void restoresV2ScriptEndingWithTerminatorWithoutLineBreak() {
        assertEquals(Samples.expected("P1020EOF.unwrapped", "P1020EOF.wrapped"),
                unwrapper.unwrapScript(Samples.read("P1020EOF.wrapped")));
    }

    @Test
    void restoresV2ScriptEndingWithTerminatorWithoutLineBreakWithCrlf() {
        String script = Samples.read("P1020EOF.wrapped").replace("\n", "\r\n");

        assertEquals(Samples.read("P1020EOF.unwrapped"), unwrapper.unwrapScript(script));
    }

    /**
     * WRAP removes {@code REM} lines outside the units, so such a line stands in a wrapped file only if it was added
     * after wrapping, as the {@code Rem drv:} lines of some Enterprise Manager files are. The restored script keeps it
     * before the first unit and between the units, as all text outside the units; WRAP 10.2.0.5 and 11.2.0.1 wrap the
     * restored script back to {@code P1020EOF} without these lines.
     */
    @Test
    void keepsRemLinesAroundV2Units() {
        String wrapped = Samples.read("P1020EOF.wrapped");
        String restored = Samples.expected("P1020EOF.unwrapped", "P1020EOF.wrapped");
        String header = "create or replace package eof_last";
        int unit = wrapped.indexOf(header);
        int restoredUnit = restored.indexOf(header);
        String script = "Rem drv: <x/>\n" + wrapped.substring(0, unit) + "Rem drv: <y/>\n" + wrapped.substring(unit);

        assertEquals("Rem drv: <x/>\n" + restored.substring(0, restoredUnit) + "Rem drv: <y/>\n"
                + restored.substring(restoredUnit), unwrapper.unwrapScript(script));
    }

    /**
     * WRAP also removes empty lines between the statements outside the units, so such lines in a wrapped file, as the
     * block of {@code Rem} lines and the empty lines around the statements of the {@code prvtqopi} and
     * {@code prvtsqlpatch} files, were added after wrapping too. The restored script keeps them before the first unit,
     * after the {@code /} of a unit and before the header of the next one, as all text outside the units.
     */
    @Test
    void keepsEmptyLinesBetweenStatementsAroundV2Units() {
        String wrapped = Samples.read("P1020EOF.wrapped");
        String restored = Samples.expected("P1020EOF.unwrapped", "P1020EOF.wrapped");
        String before = "Rem\nRem header\n\n@@start.sql\n\n\n";
        String grant = "grant execute on eof_first";
        String afterUnit = "\nRem after a unit\n\n";
        String header = "create or replace package eof_last";
        String beforeUnit = "\n  \n";
        String script = before + wrapped.replace(grant, afterUnit + grant).replace(header, beforeUnit + header);

        assertEquals(before + restored.replace(grant, afterUnit + grant).replace(header, beforeUnit + header),
                unwrapper.unwrapScript(script));
    }

    /** The same script with a line break after the last {@code /} keeps it. */
    @Test
    void restoresV2ScriptEndingWithTerminatorAndLineBreak() {
        assertEquals(Samples.expected("P1020EOF.unwrapped", "P1020EOF.wrapped") + "\n",
                unwrapper.unwrapScript(Samples.read("P1020EOF.wrapped") + "\n"));
    }

    /**
     * Each V2 unit is read in the character set of its ID: WE8ISO8859P1, WE8MSWIN1252, CL8MSWIN1251, CL8ISO8859P5,
     * CL8KOI8R and AL32UTF8 with non-ASCII identifiers and literals. Without {@code NLS_LANG} the ID is US7ASCII and
     * the bytes stay as written: valid UTF-8 is read as UTF-8, other bytes as ISO-8859-1. Units wrapped by WRAP 10.2.0.5
     * with these {@code NLS_LANG} values in one script; each restored unit written in its character set gives the same
     * unit after WRAP with the same {@code NLS_LANG}.
     */
    @Test
    void restoresV2UnitsInTheirCharacterSets() {
        assertEquals(Samples.expected("P1020CHARSETS.unwrapped", "P1020CHARSETS.wrapped", StandardCharsets.UTF_8),
                unwrapper.unwrapScript(Samples.read("P1020CHARSETS.wrapped")));
    }

    /** The restored V2 text takes the line break of the wrapped unit, the line breaks of literals included. */
    @Test
    void restoresV2UnitsWithLineBreakOfInput() {
        String script = Samples.read("P1020HEADER.wrapped").replace("\n", "\r\n");

        assertEquals(Samples.read("P1020HEADER.unwrapped"), unwrapper.unwrapScript(script));
    }

    @Test
    void reportsV2UnitsWithoutPlsqlRelease() {
        List<UnwrapResult> units = unwrapper.unwrapUnits(Samples.read("P1020HEADER.wrapped"));

        assertEquals(Arrays.asList("PROCEDURE DEMO_PROC", "PROCEDURE APP.SCH_PROC", "PROCEDURE NO_REPLACE",
                        "PROCEDURE Quoted Name", "PROCEDURE APP.Quoted_Sch", "FUNCTION MULTI_LINE", "PACKAGE APP_PKG",
                        "PACKAGE BODY APP_PKG", "TYPE APP_OBJ", "TYPE BODY APP_OBJ", "LIBRARY APP_LIB", "TYPE APP_OID"),
                units.stream().map(unit -> unit.unitType() + " " + unit.unitName()).collect(Collectors.toList()));
        assertTrue(units.stream().allMatch(unit -> "V2".equals(unit.format()) && unit.plsqlVersion() == null
                && unit.plsqlCompatible() == null && unit.staticSqlAsText() == null));
    }

    @Test
    void supportsOnlyTextsWithUnitsOfKnownFormats() {
        assertTrue(unwrapper.supports(Samples.read("P1020HEADER.wrapped")));
        assertFalse(unwrapper.supports("SELECT 1 FROM dual;\n"));
        // The header is present, but the next line is neither of V1 nor of V2
        assertFalse(unwrapper.supports("CREATE OR REPLACE PACKAGE p wrapped\nb000000\n1\nabcd\n"));
    }

    @Test
    void failsWithoutWrappedUnits() {
        assertThrows(UnwrapException.class, () -> unwrapper.unwrap("SELECT 1 FROM dual;\n"));
    }
}
