# 0007. Line endings of the restored text

[Русская версия](0007-line-endings-of-restored-text_ru.md)

## Context

The script assembly copies the text outside the wrapped units unchanged, line endings included, so the text of a
unit has to follow the line endings of the script, or the script comes out mixed.

WRAP for Linux of the V2 format writes the wrapped lines with LF. WRAP 12.1 and later keep the CR of a source with CRLF
in the text of the unit, in string literals too, so a unit with LF may hold a text with CRLF. WRAP 10.1.0.3 reads the
source line by line and replaces with a space every CR right before LF. WRAP 10.2.0.1, 10.2.0.5, 11.1.0.6 and 11.2.0.1
replace it while they read the source in blocks of 512 bytes from its start, and within a block only: a CR in the last
byte of a block, at the offset 511, 1023 and so on of the file, stays before the LF that starts the next block. A real
CRLF also stays in their text if a comment after the CR is removed: `\r-- c\n` gives `\r\n`. The CRLF these releases
keep depend on where the lines stand in the source file. A conversion of the file does not reach the text, which is
compressed.

A source may end with `/` without a line break. WRAP 10.2.0.5, 11.1.0.6 and 11.2.0.1 then end the wrapped file with `/`
without a line break too; WRAP 10.1.0.3 always writes a line break after it, and so do all of them after a `/` followed
by blanks.

## Decision

- The restored text uses the line break that ends the header line of the wrapped unit in the input: CRLF, LF or CR.
  `SourceLines.lineBreak` finds it for every format.
- If the header line has none, the line separator of the operating system is used. This applies to a unit at the
  very end of the text.
- A V2 unit stores its text as a whole (ADR 0009). LF of the text becomes the line break of the header line, and a lone
  CR stays. A CRLF of the text stays in a unit with LF, and in a unit with CR it becomes CR.
- A stray CR of a V2 text becomes a space: a CR outside string literals (`'…'`, `q'…'`, `N'…'`) and quoted identifiers
  of a text in which more line breaks outside them have a blank before them than a CR. `V2StrayCr` finds them in one
  pass over the text; comments count as outside, and a quote in a hint starts nothing. The option `strayCr` of the
  format V2 (`UnwrapOptions.withFormatOption("V2", "strayCr", "keep")`, ADR 0018) keeps the text as the unit holds it:
  `new Unwrapper(UnwrapOptions)` takes the options, and `new Unwrapper()` uses the defaults, with which the format
  reads its system property `com.github.sftwnd.oracle.unwrap.v2.strayCr` (`space` or `keep` in any case) for every
  unit; an option set explicitly wins.
- The restored text of a unit ends with the `/` line and its line break. If the `/` line of the last unit ends the
  input without a line break, the script assembly drops the line break after the restored `/`, for every format. The
  per-unit results (`unwrap`, `unwrapUnits`) keep it: they have no file around them.

## Rationale

- A script keeps its line endings, and a restored file uses one line ending unless the text of a V2 unit mixes them.
- A CRLF in the text of a unit with LF comes from WRAP and not from a conversion of the file, so it is part of the text.
  Written as LF, it would change the value of a string literal.
- The line ending needs no option: it follows the input, and an explicit choice can be added to `UnwrapOptions` later
  without breaking the API.
- The end of the input is one of its line endings, and the script assembly appends nothing to the end of the script.
  With a line break after the last `/`, WRAP 10.2 and later would give a wrapped file one byte longer.
- Of a source with CRLF, WRAP 10.2 to 11.2 keep only the CRs that stood in the last byte of a block of 512 bytes or
  before a removed comment, so where they stand depends on the source file and not on the program; the other lines of
  such a text end with the blank WRAP wrote for their CR. Outside a literal a CR separates lexemes as a space does, so
  the space changes nothing for the compiler and leaves no lone CR in a file with LF. The CR is replaced and not
  removed: a lone CR may stand between two words.
- A CR inside a literal or a quoted identifier is part of its value, which the unit is compiled with.
- WRAP 12.1 and later keep every CR of a source with CRLF, so their text has a CR and not a blank before its line
  breaks; the rule leaves it alone, and these releases give the same unit from the restored text. WRAP 10.1 leaves no
  CR before LF.
- The rule depends on the text of the unit and not on the line endings of the file, so converting the wrapped file
  changes only the line endings of the restored text.
- The stray CRs tell where the blocks of the source file ended, and only with them can the unit be wrapped back to the
  same bytes, so the option keeps them.
- `UnwrapOptions` is a class with a private constructor and `with` methods, not a record: an option added later does
  not change a constructor that callers use.
- The system property sets the option for an application that creates `Unwrapper` with the defaults, without a change
  of its code. It is read for every unit, so an application or a test can set it at run time, and an option set
  explicitly wins.

## Alternatives

- **Always CRLF.** Scripts with LF come out mixed.
- **An option of `Unwrapper` with CRLF by default.** The default keeps both problems, and the API grows.
- **The line ending of the input plus an option.** The API grows before an option is needed.
- **Converting the restored text.** It changes a CR stored in a literal.
- **A CRLF of a V2 text written as the line break of the header line.** A string literal of WRAP 12.1 and later loses its
  CR, and the restored text wraps back to a different unit.
- **A mark for a CR before LF.** `\r--\n` would wrap back to the same bytes with WRAP 10.2 to 11.2, but V2 does not store
  the release, and `--` inside a literal changes its value.
- **Every CR as the unit holds it, always.** A file with LF gets a lone CR on a few lines of a text whose other lines
  have a blank in its place, and the place depends on the source file.
- **Removing the stray CR.** A lone CR between two words would join them.
- **Replacing every CR outside literals.** The text of WRAP 12.1 and later from a source with CRLF loses its CRs, and
  the restored text wraps back to a different unit.
- **The rule in files with LF only.** In a file with CRLF the stray CR would merge with the line break, and converting
  the wrapped file would change the restored text by more than its line endings.
- **Choosing by the release.** The unit does not store it; the blanks before the line breaks tell WRAP 10.2 to 11.2.
- **A record for the options.** An option added later would change its public constructor.
- **An environment variable for the default.** It cannot be changed at run time, and a system property is the usual
  way to set up a Java library.
- **Reading the property once, when the class is loaded.** A value set later would have no effect, and tests could not
  set it.

## Consequences

- The restored text of a V2 unit mixes them only if the unit holds CRs that are not stray, as the text of WRAP 12.1 and
  later from a source with CRLF does, or if `strayCr=keep` is set. Among the 8786 V2 files of the corpus, `ords_migrate`
  and `upg_oauth` of Oracle 12.2.0.1 hold 3 and 1 stray CRLF, outside literals; 712 of 733 and 822 of 828 of their
  lines end with a blank, so their source had CRLF. By default these 4 CRs become spaces, and the restored scripts of
  all other files do not change; with `strayCr=keep` all scripts are as the units hold them. Neither form wraps back to
  the same bytes as it is: in the restored script the lines stand at other offsets, and WRAP replaces the CR there.
  With a comment before each CR that moves it to the last byte of a block, the text restored with `strayCr=keep` wraps
  back to the same files with WRAP 10.2.0.5 and 11.1.0.6, so these files are the same up to stray CRs; `\r-- c\n` on
  these lines gives the same bytes too.
- A source with CRLF whose CR stands at the offsets 511 and 1023 keeps these two CRLF in the text with WRAP 10.2.0.1,
  10.2.0.5, 11.1.0.6 and 11.2.0.1, and none with WRAP 10.1.0.3; WRAP 12.1.0.2 and 21.3 keep all of them. With
  `strayCr=keep` the library restores each of these texts as the unit holds it; by default the CRs that WRAP 10.2 to
  11.2 kept outside literals become spaces.
- The synthetic sample `P1020STRAYCR` is wrapped by WRAP 10.2.0.5 from a source with CRLF whose CRs stand at the
  offsets 511, 1023 and 1535: outside a literal, in a string literal and in a q-quoted literal. By default the first
  becomes a space and the other two stay (`P1020STRAYCR.unwrapped`); `P1020STRAYCRKEPT.unwrapped` is the text with
  `strayCr=keep`, which wraps back to the same file with WRAP 10.2.0.5 and 11.1.0.6 once a comment before each CR moves
  it to the end of a block.
- The references of the synthetic samples are stored with CRLF. For a sample wrapped by WRAP for Linux, a test
  expects the reference with LF (`Samples.expected`): that is the text the round trip of the sample was checked with.
- The sample `P1020EOF` ends with the `/` of a unit without a line break; the restored script wraps back to the same
  file with WRAP 10.2.0.5, 11.1.0.6 and 11.2.0.1. In the corpus, 3 of 2866 V2 files end that way (`prvtctx`,
  `prvtctx_v2`, `prvthtop`) and wrap back to the same bytes.
