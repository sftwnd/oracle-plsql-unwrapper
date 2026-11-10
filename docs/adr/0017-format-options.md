# 0017. Options of a single format

[Русская версия](0017-format-options_ru.md)

## Context

`UnwrapOptions` holds the options all formats share (`keepStrayCr`). A format may also have options that no other
format has: the closed implementation of V1 chooses the layout of the restored text and the parameters whose mode `IN`
is written. Until now such a setting could be given only as a system property of the process, which the help of the
command line does not mention: a user learns of it from the documentation, and an application cannot set it for one
`Unwrapper` only.

Requirements:

- the help of the command line lists what the formats on the class path or the module path accept, and nothing else;
- a format is controlled through the same objects as the shared options, without a change of the public API per option;
- the public project does not know the options of a closed format and has no dependency on it;
- the system properties keep working.

## Decision

- **Description.** `FormatUnwrapper.options()` returns `FormatOption` objects: the name, a one-line description and the
  accepted values. A format that has no options returns an empty list (the default).
- **Values.** `UnwrapOptions.withFormatOption(format, name, value)` stores the value under `format.name`;
  `formatOption(format, name)` returns it or `null`. The object stays immutable. A format takes its values in
  `FormatUnwrapper.withOptions` and ignores what it does not know. It validates the values there: a wrong value is an
  `IllegalStateException`, which the command line reports.
- **Help.** `Unwrapper.formatOptions()` lists the options of the formats that have any. The help of the command line
  shows them under the formats, as `V1.layout=source|canonical`, and mentions `--format-option` only if there are any.
- **Command line.** `--format-option <format>.<name>=<value>`, repeated for several options; a later one of the same
  name wins. After the unwrapper is created, the names are checked against `formatOptions()`: an option no format
  lists is a wrong argument (exit code 2), because a format would ignore it silently.
- **Properties.** An option wins over the system property of the same setting; what an option does not set stays with
  the property, which is read for every unit as before.

## Rationale

- One generic option of the command line needs no change when a format gets a new setting: the format describes it, the
  help and the check follow.
- Descriptions come from the format, so the help of a distribution without V1 does not mention it, and the public
  project stays free of names from the closed one.
- Keeping the values as strings in `UnwrapOptions` avoids types of the closed module in the SPI; the format converts
  them and rejects the wrong ones with its own message.
- The check of the names in the command line catches a misspelt option, which the tolerance of the formats would hide.

## Alternatives

- **A flag per setting** (`--v1-layout`). Easier to type, but the command line would have to know every setting of every
  format, public or not.
- **Typed options in `UnwrapOptions`.** Puts the types of each format into the SPI.
- **Only system properties.** Nothing to change, but the help cannot list them and one process cannot use two settings.

## Consequences

- A format may add options without a change of the public API; the SPI grows by `FormatOption`,
  `FormatUnwrapper.options()` and the two methods of `UnwrapOptions`.
- A user finds the options of a format in `--help`, and an application sets them per `Unwrapper`.
- A value is not checked by the command line itself: a wrong one is reported by the format on creation of the
  unwrapper, with exit code 1.
