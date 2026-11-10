# 0018. Stray CR as an option of the format V2

[Русская версия](0018-stray-cr-as-option-of-format-v2_ru.md)

## Context

What becomes of a stray CR of a V2 text (ADR 0007) is the only setting that `UnwrapOptions` held as its own field,
with a `with` method, a getter and a system property of the API package. Only the format V2 reads it: the text of a
unit of V1 never holds a stray CR, and the V1 implementation takes its own options (`layout`, `in`) through
`FormatUnwrapper.withOptions` and ignores it. The setting came before the mechanism of options of a single format
(ADR 0017), under which a format describes its options itself and the command line sets them by name.

The release 1.0.0 is published with that API: `UnwrapOptions.withKeepStrayCr`, `keepStrayCr()`,
`KEEP_STRAY_CR_PROPERTY`, the constructor `V2FormatUnwrapper(boolean)` and the flag `--keep-stray-cr` of the command
line.

## Decision

- The setting is the option `strayCr` of the format V2 with the values `space` (the default) and `keep`:
  `UnwrapOptions.withFormatOption("V2", "strayCr", "keep")`, `--format-option V2.strayCr=keep`.
  `V2FormatUnwrapper.options()` describes it, so the help of the command line lists it under the format, and
  `withOptions` takes it and rejects another value with `IllegalStateException`, as V1 does with its options.
- What the option does not set is read by the format from its system property
  `com.github.sftwnd.oracle.unwrap.v2.strayCr`, for every unit, as the properties of V1 are; the option wins.
- `UnwrapOptions` holds only the options of the formats, by name. `defaults()` sets none and reads no property.
- The API of the release 1.0.0 keeps working and is deprecated for removal in the next major release:
  `withKeepStrayCr(true)` is `withFormatOption("V2", "strayCr", "keep")` and `withKeepStrayCr(false)` sets `space`;
  `keepStrayCr()` tells whether the option is `keep`; the property `com.github.sftwnd.oracle.unwrap.keepStrayCr`
  (`true` in any case keeps the CR) is read by V2 after its own property; `V2FormatUnwrapper(boolean)` sets the
  option; `--keep-stray-cr` adds the option `V2.strayCr=keep` where it stands among the arguments, and is left out of
  the help.

## Rationale

- A setting that one format reads belongs to that format: the help describes it only where the format is on the class
  path, and the SPI carries no knowledge of what the formats do with the text.
- The same mechanism for every format keeps one way to set an option in the API, the command line and the system
  properties: `V1.layout`, `V1.in` and `V2.strayCr` differ only by name.
- The values `space` and `keep` name what happens to the CR, as `source` and `canonical` name the layout; a boolean
  would have named only one of them.
- The deprecated API maps onto the option without a second code path: the format reads the option as the only source,
  and the old property only after its own, so a process that sets the old one gets the old result.
- The output does not change: the default stays `space`, and the samples are compared as stored.

## Alternatives

- **Leave the field in `UnwrapOptions`.** The one shared option would be the one that only V2 reads, and every new
  option of V2 would face the same choice.
- **Remove the old API at once.** A library used from other code does not drop a public method in a patch release.
- **A boolean option `keepStrayCr=true`.** Keeps the old name but not the style of the options of V1, and `false`
  says less than `space`.
- **`--keep-stray-cr` in the help as a second way.** Two ways to say the same thing in the help, one of them going.

## Consequences

- `UnwrapOptions` loses its own field; `equals`, `hashCode` and `toString` cover the options of the formats only.
- `V2FormatUnwrapper` gains `STRAY_CR_PROPERTY`, `STRAY_CR_OPTION`, `options()` and the check of the value;
  `V2UnitParser` takes the setting per unit.
- The command line always mentions `--format-option`, because V2 always has an option.
- The deprecated members are removed in the next major release, with the alias of the property and the flag.
