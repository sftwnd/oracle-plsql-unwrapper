# 0009. Format V2 (Oracle 10g and later)

[Русская версия](0009-format-v2_ru.md)

## Context

From Oracle 10g on, WRAP writes units in format V2. A V2 unit has no DIANA tree: it holds the source text normalized
by WRAP, compressed and enciphered. The layout below was checked against the output of WRAP 10.2.0.5 for Linux x86_64
(issue #57), of WRAP 10.1.0.3 for Linux x86 (issue #59), of WRAP 11.1.0.6 for Linux x86_64 (issue #60) and of WRAP
11.2.0.1 for Linux x86_64 (issues #61 and #62):

| Line | Content |
|---|---|
| 1 | the header: the `CREATE` statement copied from the source up to the end of the unit name, or of the last of the SHARING, TIMESTAMP and OID clauses after it, then ` wrapped `; it may span several lines |
| 2 | `a000000` in every known release; a V1 unit has `0` there |
| 3 | hexadecimal ID of the client character set (`NLS_LANG`): `1` US7ASCII, `1f` WE8ISO8859P1, `ab` CL8MSWIN1251, `369` AL32UTF8 and so on; without `NLS_LANG` it is `1` whatever the bytes are |
| 4–18 | `abcd` |
| 19 | unit kind, the hexadecimal object type of the data dictionary: `7` procedure, `8` function, `9` package, `b` package body, `d` type, `e` type body, `16` library, `57` assembly (WRAP 11.1.0.6 and later) |
| 20 | two hexadecimal lengths: the text with its terminating NUL, and the Base64 characters plus one per full line of 72 |
| 21... | Base64 lines of 72 characters, then an empty line and a `/` line |

The data is decoded in four steps: Base64; a fixed substitution of every byte by a table of 256 bytes; a check that
the first 20 bytes are the SHA-1 digest of the rest; inflating the rest, a zlib stream of the text followed by NUL. The
table was published with a public-domain script by Niels Teusink; every entry occurs in the verified data.

WRAP normalizes the text before compressing it:

- the text starts with the kind and the name of the unit, without `CREATE`, `OR REPLACE` and the schema; the words of
  the kind are in upper case; WRAP 10.2.0.5 separates them with single spaces and spells the name as in the source,
  WRAP 10.1.0.3 keeps the blanks of the source, writes spaces in place of the schema and writes the name in upper case;
- unquoted words are upper-cased by the rules of the client character set; string literals, quoted identifiers and
  hints keep their case;
- comments are removed and their line breaks stay; a comment between two words is removed without a separator:
  `tz_obj/* c */as` becomes `tz_objAS`;
- WRAP 10.1.0.3 to 11.2.0.1 for Linux replace a CR right before LF with a space, in string literals too, WRAP 10.2 to
  11.2 only within each block of 512 bytes they read, and WRAP 12.1 and later keep it (ADR 0007);
- WRAP 10.2.0.5 and 11.1.0.6 end the text with the last `;` of the unit, unless a comment follows it: then the text
  goes up to the comment, with the blanks and line breaks before it: `end; -- c\n/` gives `END; `, `end;\n\n-- c\n/`
  gives `END;\n\n`;
- WRAP 10.1.0.3 ends the text with the last `;` only if the line ends right after the `;` and the next line is the `/`
  line, and otherwise keeps the text up to the `/`: `end;\n/` and `end;\n  /` give `END;`, `end;\n\n/` gives
  `END;\n\n`, `end;   \n/` gives `END;   \n`, and `end;   \n  /` gives `END;   \n  `; it keeps the text up to a
  comment after the last token too, and the line break after a `--` comment: `end; -- c\n/` gives `END; \n`;
- every release removes a block comment after the last token together with the next line break: `end; /* c */\n/`
  gives `END; `, and `end;\n/* c */\n/` gives `END;\n`;
- a unit without a `/` line takes the text up to the next `/` line, the following statements included, or up to the end
  of the file with its line break.

Only the header keeps `CREATE`, `OR REPLACE`, `IF NOT EXISTS`, the schema, the spelling of the source and the clauses
after the name. Every release copies the TIMESTAMP clause of every kind of unit, with a string literal or `OLD`, and the
OID clause of a type into the header; WRAP 10.1.0.3 copies the OID clause of every kind, and WRAP 10.2.0.1 and later
leave it in the text of other kinds. WRAP 10.2.0.5 leaves the clauses out of the text. WRAP 10.1.0.3 replaces with
spaces every character from the first clause to the character after the last clause, line breaks and comments included,
and copies that character into the header: `OID '…'\n wrapped` and `OID '…'  wrapped`, where WRAP 10.2.0.5 writes `OID
'…' wrapped`. A unit other than a type gets NUL there if the last clause ends its line: WRAP 10.1 reads the source line
by line and finds the end of the line, while for a type it has read the next line already.

WRAP 23.26.1 copies `IF NOT EXISTS` between the kind and the name into the header and leaves it out of the text, in
every kind of unit; WRAP 10.2.0.1 to 21.3 take `IF` for the name of such a unit. WRAP 11.1.0.6 and later also wrap units
of the kind `ASSEMBLY`, which the text starts with like any other kind.

WRAP 12.1 and later do the same with the SHARING clause of every kind of unit if it stands right after the name: it goes
into the header as written, with its blanks and comments, and out of the text. Elsewhere it stays in the text: after
`AUTHID`, `ACCESSIBLE BY` or the return type of a function. In a type it stands before the TIMESTAMP and OID clauses: WRAP
leaves a type with SHARING after OID as plain text, and so it does with the values `DATA` and `EXTENDED DATA`.

Outside the units WRAP copies the source, but after the `/` line of a unit or of an anonymous block WRAP 10.2.0.5 and
11.1.0.6 drop the blanks before the next statement, plain SQL or the header of a unit; after the `/` of an SQL
statement they keep them. A `--` comment line right after a `/` line is removed by every release, and the blanks after
it stay: `/\n-- c\n  grant …` gives `/\n  grant …`. WRAP 10.1.0.3 keeps the blanks before plain SQL and drops them
before a header in any case. WRAP 10.1.0.3 to 11.2.0.1 keep a block comment outside a unit in the output, and WRAP
12.1 and later remove one that starts a line and keep the rest of the line.

Some lines outside the units are removed before the first unit, between the units, among plain SQL and inside an SQL
statement over several lines: `REM` lines (`REM`, `Rem` or `rem`, alone or with text, also after blanks), `--` comment
lines, empty lines and lines of blanks. All releases from 9.2.0.4 to 23.26.1 remove them; `REMARK` lines stay. Inside
an anonymous block, up to its `/` line, WRAP 9.2.0.4 and 10.1.0.3 remove them too, and WRAP 10.2.0.1 to 23.26.1 keep
empty lines, lines of blanks and `REM` lines and leave an empty line in place of a `--` line. Such a line between the
statements of a wrapped file stands there only if it was added after wrapping.

WRAP also copies the blanks before the header, except where it drops them after a `/`. Real scripts have headers WRAP
does not write: text taken from the data dictionary (`ALL_SOURCE`) starts with the kind, and `DBMS_METADATA` writes
`EDITIONABLE` or `NONEDITIONABLE` (Oracle 12c and later), which WRAP itself writes from 21.3 on, and quoted names.
Scripts also pass through SQL clients and version control that split the Base64 lines and change the line endings.

The unit does not store the PL/SQL release. WRAP 11.1.0.6 gives the same files as WRAP 10.2.0.5 for all the sources
checked. WRAP 11.2.0.1 gives the same text, but compresses some texts differently (2 of 169 test scripts). WRAP 12.1
to 23.26.1 write the same layout and differ in the details above; the behavior of each release is described in
[WRAP of the format V2: behavior by release](../wrap-v2-behavior.md).

## Decision

- `V2FormatUnwrapper` in the package `com.github.sftwnd.oracle.unwrap.v2` of the module `oracle-plsql-unwrap-v2`, a
  provider of the service `FormatUnwrapper` (ADR 0011). The other classes of the package are not public:
  - `V2UnitParser` finds and reads units;
  - `V2Header` holds the header;
  - `V2Data` decodes the data;
  - `CharacterSets` decodes the text in its character set;
  - `V2Source` builds the restored statement;
  - `V2Unit` is the `WrappedUnit` of the format.
- A header starts at a line start, after spaces and tabs only:
  `[CREATE [OR REPLACE] [EDITIONABLE | NONEDITIONABLE]] kind [IF NOT EXISTS] [schema.]name [SHARING = value]
  [TIMESTAMP {'ts' | OLD}] [OID 'oid'] wrapped`. The kind is `PACKAGE [BODY]`, `TYPE [BODY]`, `PROCEDURE`, `FUNCTION`,
  `LIBRARY` or `ASSEMBLY`. Every kind may have the TIMESTAMP and OID clauses, each alone or both in this order: WRAP
  10.2.0.5 leaves a type with OID before TIMESTAMP as plain text. `IF` is the name unless `NOT EXISTS` follows it. The
  words may be separated by spaces, line breaks and comments, after the last clause also by the NUL of WRAP 10.1, and
  the parts of the name may be quoted. `wrapped` ends its line, and the next line is `a000000`.
- A line that ends with `wrapped` and is followed by `a000000`, a line and `abcd` is a unit even if its header does not
  match this form. `find` returns that line and `read` reports it with `UnwrapException`, so `supports` is true for the
  text and `unwrap`, `unwrapUnits` and `unwrapScript` fail. Without these lines the text is plain text.
- The parser reads the unit up to the end of its Base64 lines; the script assembly drops the empty line and the `/`
  line after them. The Base64 lines end at an empty line, a `/` line, a line with other characters or the end of the
  text; blanks around them are dropped, and their length does not matter. The unit kind and the second length are
  read but not used: the kind is taken from the header, and the second length changes with the line endings.
- The text is restored only if the digest matches, the zlib stream ends where the data ends, the text ends with NUL
  and its length with the NUL equals the first length. Otherwise `UnwrapException` names the line of the first Base64
  line.
- The text is decoded by the Java character set of its ID; the table holds the 25 IDs written by WRAP 10.2.0.5 for
  the character sets checked. If the ID is unknown, the Java platform lacks the character set, or the bytes are
  invalid in it, the text is UTF-8 if the bytes are valid UTF-8 and ISO-8859-1 otherwise.
- The restored statement is the header as WRAP copied it, with the blanks before it on its line, the text of the unit
  after its kind and name, and the `/`. If the name in the text runs on into the next word, a space separates them.
  A header without `CREATE` gets `CREATE OR REPLACE `. LF in the text becomes the line break of the header line, and a
  CRLF stays in a unit with LF (ADR 0007); a lone CR stays. A stray CR becomes a space unless the option `strayCr` of the
  format is `keep` (ADR 0007).
- If the last clause is followed by a character before ` wrapped ` and the text after the name starts with at least as
  many blanks as the header takes up to that character, the character goes after the clauses and these blanks are left
  out; NUL stands for a line break. The header is counted from the end of the name, without the comments before the
  first clause and with a CRLF as one character. Otherwise the text stays as it is.
- A text that ends with a blank or a line break gets an empty comment `/**/` after it; the `/` takes a line of its own.
- After a V2 unit, up to the first line of the next unit, a `--` line goes before each line that follows a `/` line and
  holds blanks followed by something else. `WrappedUnit.dropsIndentAfterSlash()` tells the script assembly to do so;
  its default keeps the text after the units of other formats as it is.
- `plsqlVersion`, `plsqlCompatible` and `staticSqlAsText` of a V2 unit are `null`.
- The line break of the header line and the number of a line are common to the formats: `SourceLines` in the `spi`
  package.

## Rationale

- The header of the wrapped unit stays as WRAP copied it: the schema and `OR REPLACE` come from the input, the only
  place that keeps them, and decide where the unit is created.
- WRAP 10.2.0.5 and 11.1.0.6 give the same wrapped file from the restored text, except for a name that runs on into
  the next word (see below).
- A text ends with a blank only where WRAP removed a comment, at the end of a file without a `/` line, or, in WRAP 10.1,
  where the source has blanks before the `/`. Every release keeps the text before a block comment at its end and
  removes the comment with the next line break, so the empty comment gives back the same text whatever release wrapped
  the unit. Without it WRAP 10.2 and later would cut the blanks, and a line break before the `/` would become a part of
  the text in WRAP 10.1.
- After the `/` of a unit or of an anonymous block, blanks at the start of a line stay in the output of WRAP 10.2 and
  later only because of a removed comment line. Every release removes the comment line again, so writing it also where
  WRAP keeps the blanks anyway, after the `/` of an SQL statement or in WRAP 10.1, does not change the wrapped file.
  The script assembly learns the format from the units, so the rule starts after the first V2 unit; WRAP of V1 was not
  checked.
- The marks are empty comments: they change neither the code of the unit nor the unit WRAP builds from it.
- The spaces of WRAP 10.1.0.3 stand for the clauses, so writing them after the clauses would double the distance to the
  next word on every pass. The header tells the two forms apart: the character after the last clause is there only in
  WRAP 10.1, and the blanks in the text confirm it. WRAP has no CR in a header, so a CRLF there comes from a change of
  the line endings after wrapping and stands for one character of the text.
- The digest and the first length catch damaged and truncated data. The second length does not survive a change of
  line endings, so checking it would reject valid units.
- The unit names its character set, so no guessing is needed. A unit wrapped without `NLS_LANG` has the ID of US7ASCII
  over any bytes; UTF-8 is the common case there, and ISO-8859-1 keeps every other byte, so the caller can write them
  back unchanged.
- A name that runs on into the next word was separated by a comment in the source. Without a space the statement does
  not compile; with it, the restored text differs from the stored one by that space only.
- A V2 unit is all source text, so the form of its static SQL does not tell anything.
- The headers of the data dictionary and of `DBMS_METADATA` are the usual way V2 units leave a database; they follow
  the documented `CREATE` syntax.
- The header of V2 is parsed separately from V1: V1 requires `CREATE` and reads the header as part of its line-numbered
  parser, while V2 accepts other header forms and ends the header with a marker line. The formats share the rule of
  the header text, and the line helpers in `spi`.
- WRAP 10.1 has NUL in a header only after a clause at the end of a line, and a line break there gives the same NUL on
  the next pass. Written into the restored text, NUL would make it unusable.
- `IF` is a valid name, and WRAP 10.2 to 21.3 write headers with the name `IF`, so the words are taken for
  `IF NOT EXISTS` only when all three stand there.

- An unknown header is an error and not plain text: a script with a unit left wrapped looks restored, and a round trip
  does not show it, because WRAP copies a wrapped unit unchanged. Only a unit has the lines after `wrapped`. `read`
  reports it and `find` does not, so the check `supports` works without an exception.

## Alternatives

- **`CREATE OR REPLACE ` followed by the text, as public tools do.** The schema is lost and the unit is created in the
  schema of the user; `CREATE` without `OR REPLACE` and the spelling of the header are lost too.
- **Copying a unit with an unknown header as plain text.** The caller gets a script with wrapped code in it and has no
  way to know.
- **Taking any text between the name and `wrapped` for clauses.** Whether WRAP also keeps such a clause in the text is
  unknown, so the restored text could be wrong without an error.
- **Guessing the character set from the bytes.** Unnecessary when the unit names it, and wrong for short texts.
- **Decoding invalid bytes as replacement characters.** The bytes cannot be written back.
- **Checking the second length or the line layout.** Valid units would fail after a change of line endings or of
  the line length.
- **Failing on an unknown ID.** The text is still readable, and the bytes are kept.
- **The character set in `UnwrapResult` with an option to override it.** Not needed yet; both can be added later
  without breaking the API.
- **One header parser for V1 and V2.** Both formats would depend on options that only one of them needs, and a change
  for V2 could change V1 results verified by the round trip.
- **Telling WRAP 10.1 by the name in upper case.** The name may be in upper case in the source, and the unit does not
  store the release.
- **No marks, with the traces of comments as a documented limitation.** The script would hold no comment that is not
  in the unit, but 72 files of the V2 corpus would not wrap back to the same bytes.
- **Marks behind an option of `Unwrapper`.** An addition to the public API for marks that change neither the unit nor
  the result of WRAP.
- **Leaving out the `/` of a unit whose text ends with a line break, or letting the `/` continue a blank last line.**
  The script would have no terminator, and WRAP 10.2 and later cut the blanks before such a `/`.
- **A `--` comment at the end of the text.** WRAP 10.1 keeps the line break after it.
- **A block comment before the blanks after a `/`.** WRAP 10.2 and later keep a block comment outside a unit in the
  output.

## Consequences

- The synthetic samples `P1020*` are wrapped by WRAP 10.2.0.5 and `P1010*` by WRAP 10.1.0.3, and the round trip with
  the same release compares the wrapped files byte by byte:
  - `P1020HEADER` — all unit kinds, headers with the schema, without `OR REPLACE`, with quoted names, over several
    lines, with a comment before `IS`, with the OID clause of a type and with blanks before a unit that follows plain
    SQL, plain SQL between the units: the same file;
  - `P1020TYPE` — types with the TIMESTAMP and OID clauses: both, each alone, over several lines and in other case,
    with a type body, a type without the clauses and plain SQL between the units: the same file;
  - `P1020CHARSETS` — units of the IDs `1f`, `b2`, `ab`, `23`, `c4`, `369` and of `1` with UTF-8 and ISO-8859-5
    bytes, Cyrillic and Latin-1 identifiers and literals; each unit, written in its character set and wrapped with the
    same `NLS_LANG`, gives the same unit; the reference is stored in UTF-8;
  - `P1020EDGE` — a type body with a comment before `AS` and units without the `/` line: the same file except for
    the space after the glued name;
  - `P1020COMMENT` — comments after the last token with blanks and blank lines before them, comment lines after a `/`
    before plain SQL, before a unit and after an anonymous block, and an indented line after the `/` of an SQL
    statement: the same file; WRAP 11.1.0.6 gives the same file from the source and from the restored text;
  - `P1020EOF` — a script that ends with the `/` of a unit without a line break (ADR 0007): the same file; WRAP
    11.1.0.6 and 11.2.0.1 give the same file from the source and from the restored text;
  - `P1010TAIL` — units with blank lines, with blanks after the last token and on the last line, with a `/` after
    blanks right after the last line and after other lines, plain SQL between the units: the same file;
  - `P1010TYPE` — types with the TIMESTAMP and OID clauses over several lines, on the line of the name, with a tab, with
    comments before, among and after the clauses, with `AUTHID` and `UNDER` after them, a type body and a type without
    the clauses: the same file;
  - `P1010CRLF` — types and a procedure with a line break in a literal, wrapped from a source with CRLF: the same file;
  - `P1010EDGE` — comments after the last token and a unit without the `/` line at the end of the file: the same file;
  - `P1010COMMENT` — the source of `P1020COMMENT`: the same file;
  - `P1020CLAUSES` — the TIMESTAMP clause in every kind of unit, with a string literal and with `OLD`, over several
    lines and after a comment, the OID clause of a type and of a procedure: the same file, and WRAP 11.1.0.6 gives it
    too;
  - `P1010CLAUSES` — the TIMESTAMP and OID clauses in every kind of unit, with `OLD`, with a tab and at the end of a
    line, where a unit other than a type gets NUL in the header: the same file.
- The synthetic sample `P1110ASSEMBLY` is wrapped by WRAP 11.1.0.6: units of the kind `ASSEMBLY` with a schema, a
  quoted name, a line break and the TIMESTAMP clause. WRAP 11.1.0.6, 11.2.0.1, 12.1.0.2, 21.3 and 23.26.1 give the same
  file from the restored text.
- The synthetic sample `P1210CRLF` is wrapped by WRAP 12.1.0.2 from a source with CRLF: units with CRLF, with CRLF in a
  literal, with both CRLF and LF, with a header over several lines, and plain SQL with CRLF. WRAP 12.1.0.2, 12.2.0.1 and
  21.3 give the same file from the restored text.
- The synthetic sample `P1210SHARING` is wrapped by WRAP 12.1.0.2: the SHARING clause in every kind of unit, in other
  case, with blanks and comments, over several lines, before OID and in places where it stays in the text. WRAP 12.1.0.2
  and 21.3 give the same file from the restored text.
- The synthetic sample `P1210CLAUSES` is wrapped by WRAP 12.1.0.2: the SHARING clause followed by the TIMESTAMP clause
  with a string literal and with `OLD`, and by the OID clause of a type. WRAP 12.1.0.2, 12.2.0.1, 21.3 and 23.26.1 give
  the same file from the restored text.
- The synthetic sample `P23261EXISTS` is wrapped by WRAP 23.26.1: `IF NOT EXISTS` in every kind of unit, with
  `EDITIONABLE`, a schema, the SHARING and TIMESTAMP clauses, a comment and a line break between its words and before a
  quoted name `"If"`, and a unit with `OR REPLACE`. WRAP 23.26.1 gives the same file from the restored text.
- All 25 IDs of the table pass the round trip with their `NLS_LANG`, and so does a unit with 70 KB of text.
- The texts of the corpus `PLS/V1` restored by the library and wrapped by WRAP 10.2.0.5 give 2382 files with 4622 V2
  units. All units are restored, and WRAP gives the same file again for 2381 of them. The last one differs only in a
  line break that WRAP adds to plain text at the end of a file without a `/` line on every pass.
- The 679 V2 files of the Oracle 10.1.0.3 distribution, 1877 units, are all restored, and WRAP 10.1.0.3 gives the same
  file for all of them; 7 of them need the empty comment.
- The 601 V2 files of the Oracle 11.1.0.6 distribution, 1675 units, are all restored, and WRAP 11.1.0.6 gives the same
  file for all of them. 24 of them need the marks: 22 units end where WRAP removed a comment, and 11 lines after a `/`
  keep the blanks after a removed comment line.
- In the V2 files of the three distributions, 1929 files, the marks change the restored script of 138 files. 72 of them
  wrap back to the same bytes only with the marks, and the other 66 wrap back to the same bytes with and without them.
- The 937 V2 files of the Oracle 11.2.0.1 distribution are all restored. WRAP 11.2.0.1 gives the same file for 736 of
  them, and WRAP 10.1.0.3 for the 195 files of APEX. WRAP 10.2.0.1 gives the same file for 192 of them, but not for the
  other 3: they have several blanks between the kind and the name, which only WRAP 10.1 keeps, so APEX was wrapped by
  WRAP 10.1. The other 6 files, of Enterprise Manager, start with a `Rem drv: <…>` line added after wrapping.
- 7 V2 files of the Oracle 12.1.0.2, 21.3 and 23.26.1 distributions have the SHARING clause in the header. All are
  restored, and WRAP of the same release gives the same file for them.
- Text that WRAP removes outside the units, such as these `REM` lines, is kept in the restored script as all text
  outside the units, and wrapping the script again removes it. A file with such text wraps back to the same file without
  these lines: the 6 files of Enterprise Manager do so with WRAP 11.2.0.1. So do the 8 V2 files `prvtqopi` and
  `prvtsqlpatch` of the corpus, with a block of `Rem` lines and empty lines between the statements outside their unit,
  with WRAP 11.2.0.1 to 23.26.1; the empty lines inside the anonymous block of `prvtsqlpatch` stay.
- Comments inside a V2 unit cannot be restored, and unquoted words come back in upper case. Where WRAP left a trace of
  a removed comment, the restored script has an empty comment.
- The restored text holds the characters of the character set of each unit. The script is written in an encoding that
  covers them: the one the units were wrapped in, or UTF-8.
- WRAP 10.1.0.3 to 11.2.0.1 for Linux accept CRLF but replace a CR before LF with a space, WRAP 10.2 to 11.2 only within
  each block of 512 bytes they read, and WRAP 12.1 and later keep it. A restored text with CRLF from a file with CRLF is
  converted to LF before it is wrapped again.
- A unit is wrapped again by the release that wrapped it: all of them write `a000000`, but WRAP 10.1 gives a different
  text from the same source than WRAP 10.2.0.5, 11.1.0.6 and 11.2.0.1, and WRAP 11.2.0.1 may compress it differently.
- The header forms are checked on the corpus up to Oracle 23.26.1, 8786 V2 files. A header with a clause the parser
  does not know fails with `UnwrapException`, and so does a text that does not start with the kind and the name of its
  header.
