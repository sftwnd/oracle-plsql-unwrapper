# 0016. Test references as plain bytes with a checksum manifest

[Русская версия](0016-test-references-with-checksums_ru.md)

## Context

The tests compare the output of the unwrapper byte for byte with references: the units wrapped by WRAP
(`<sample>.wrapped`) and the texts they restore to (`<sample>.unwrapped`), in the directory `samples` of a format. The
bytes are the point of a reference. WRAP for Windows writes CRLF and WRAP for Linux writes LF, a sample of carriage
returns mixes them with lone CRs, lines of a wrapped unit end with blanks, a script may end without a line break, and a
unit of V2 may hold a NUL or a text in UTF-8. Every reference is synthetic: its text belongs to the project, and WRAP of
the same release builds the stored unit from it again.

Git and editors change exactly these bytes. With `core.autocrlf=input` git turns CRLF into LF when a file is added;
with `core.autocrlf=true`, the default of Git for Windows and of the Windows runners of GitHub, which run `verify` in
`native.yml`, it turns LF into CRLF on checkout. Such a change does not show in `git status`, and it changes a wrapped
unit and its reference alike: the tests still pass, without testing the line break the unit was written with. Editors
trim trailing blanks and add a final line break.

A reference is also what the review of a fix reads: a change of the unwrapper shows as the lines of the references it
changes.

## Decision

- A reference is stored as plain bytes, as WRAP wrote it or as the unwrapper restored it; `Samples.read` reads the
  bytes.
- `.gitattributes` sets `-text` for `*.wrapped`, `*.unwrapped` and `SHA256SUMS`: git converts none of their line
  breaks, whatever `core.autocrlf` is. It sets `-whitespace` for the references: `git diff` and `git apply` take none
  of their blanks and CRs for errors, and `git apply --whitespace=fix` leaves them. `*.wrapped` is
  `linguist-generated`: GitHub folds the output of WRAP in a pull request.
- `.editorconfig` keeps editors from trimming trailing blanks and adding a final line break in the references.
- A directory of references holds the manifest `SHA256SUMS` in the format of `sha256sum`: a line `<hash>  <path>` for
  every reference below it, sorted by path. `ReferenceChecksums.mismatches` of the SPI test jar compares a directory
  with its manifest both ways and lists every difference: a listed file that is missing or has another checksum, with
  its line breaks now, and a reference that is not listed. `SampleChecksumsTest` of V2 checks `samples`.
- The check runs in the module that owns the references. A project that takes the test jars of several formats has
  the directories `samples` of all of them under one path of the class path.
- A reference changes together with its line in the manifest. The manifest is rebuilt in its directory with
  ```
  find . -type f \( -name '*.wrapped' -o -name '*.unwrapped' \) | sed 's|^\./||' | LC_ALL=C sort \
      | xargs shasum -a 256 > SHA256SUMS
  ```
  and checked without Java with `shasum -a 256 -c SHA256SUMS`.

## Rationale

- Plain bytes give the review the lines a fix changes, let git compress the units of one source wrapped by several
  releases as deltas of each other, and make a new sample the file WRAP wrote, with no tool to encode it.
- The attributes keep the bytes where git would change them; the manifest shows a change wherever it comes from: git
  without the attributes, an editor, a copy, the filtering of resources by Maven. The check reads the references from
  `target/test-classes`, the copy the tests read.
- The manifest is checked both ways, so a reference added without its checksum fails the build too, and so does a
  file of another origin put among the references.
- SHA-256 in the format of `sha256sum` needs no tool of the project: a standard command builds and checks the manifest.
- One manifest for a directory of references, not one per subdirectory: a reference that moves to another
  subdirectory changes one line.

## Alternatives

- **A Base64-encoded zip archive for each reference.** Keeps the bytes on any checkout, since the decoder skips line
  breaks, and keeps the text out of plain sight, which matters only for code that may not be published. A change of one
  line rewrites the whole file in a diff, git cannot compress such files as deltas, and a new reference needs an
  encoding tool. The references are synthetic, so there is nothing to keep out of sight.
- **The line break in the name of a reference (`_lf`, `_crlf`).** Repeats what the build of WRAP decides, and has no
  name for the samples that mix line breaks, the ones the line breaks matter most for. A name states the bytes; it does
  not keep them.
- **The extensions `.sql` and `.plb`.** An IDE takes such a file for SQL: it inspects it, and a reformatting of the
  directory rewrites its bytes. The extensions of the references also keep them apart from wrapped and restored files of
  any other origin.
- **`.gitattributes` alone.** Prevents the conversions of git, not the changes of an editor or a tool, and leaves
  nothing that shows a change.

## Consequences

- A fix that changes a reference changes its line in `SHA256SUMS`, and the diff of the manifest lists the references
  the fix changes.
- The build fails if a reference differs from the manifest, naming the file and its line breaks.
- A diff shows CRLF as `^M`, and a unit with a NUL as a binary file.
- A new kind of reference, with another extension, needs a line in `.gitattributes`, in `.editorconfig` and in
  `ReferenceChecksums`.
