# Oracle PL/SQL Unwrapper

[![Maven Central](https://img.shields.io/maven-central/v/com.github.sftwnd/oracle-plsql-unwrapper)](https://central.sonatype.com/artifact/com.github.sftwnd/oracle-plsql-unwrapper)
[![javadoc](https://javadoc.io/badge2/com.github.sftwnd/oracle-plsql-unwrapper/javadoc.svg)](https://javadoc.io/doc/com.github.sftwnd/oracle-plsql-unwrapper)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue)](#modules-osgi-and-services)
[![JPMS](https://img.shields.io/badge/JPMS-named%20modules-blue)](#modules-osgi-and-services)
[![OSGi](https://img.shields.io/badge/OSGi-bundles-blue)](#modules-osgi-and-services)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_oracle_plsql_unwrapper&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sftwnd_oracle_plsql_unwrapper)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_oracle_plsql_unwrapper&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sftwnd_oracle_plsql_unwrapper)
[![Build](https://github.com/sftwnd/oracle-plsql-unwrapper/actions/workflows/build.yml/badge.svg)](https://github.com/sftwnd/oracle-plsql-unwrapper/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/sftwnd/oracle-plsql-unwrapper)](https://github.com/sftwnd/oracle-plsql-unwrapper/blob/master/LICENSE)

[Русская версия](README_ru.md)

**Restores PL/SQL source code from Oracle wrapped scripts of Oracle 10g and later: a library and a command line for Java 8+.**

A library for restoring PL/SQL source code from the Oracle wrapped format V2 (Oracle 10g and later). The public API is in the `com.github.sftwnd.oracle.unwrap` package, and the options and the exception that the formats share are in `com.github.sftwnd.oracle.unwrap.spi`.

A unit of the format V1 (Oracle 7.2, 7.3, 8, 8i and 9i) is recognized, but unwrapping it is not implemented: `UnwrapException` reports it, with the contacts of the author, who licenses the closed module that unwraps V1: ashindarev@gmail.com or [Andrey Shindarev on LinkedIn](https://www.linkedin.com/in/ashindarev/). A V2 unit records no PL/SQL version.

## Requirements

- JDK 8 or newer to run. To build, JDK 8 gives jars without JPMS descriptors, JDK 9 or newer jars with them.
- Internet access the first time Maven and its plugins are downloaded.

You do not need to install Maven separately: the project includes the Maven Wrapper.

## Build

From the project root, run:

```bash
./mvnw clean package
```

On Windows:

```bat
mvnw.cmd clean package
```

The build runs the tests: they compare the output byte by byte with the reference files in `oracle-plsql-unwrap-v2/src/test/resources/samples`. The references are stored as plain bytes, and `SHA256SUMS` next to them lists their checksums, which a test checks: line breaks and trailing blanks must survive git and editors, see [ADR 0016](docs/adr/0016-test-references-with-checksums.md).

The profile `coverage` adds JaCoCo, with the report of all the modules in `oracle-plsql-unwrapper/target/site/jacoco-aggregate`. GitHub Actions build the project on JDK 8, 11, 17, 21 and 25 and analyze it in SonarCloud, see [ADR 0014](docs/adr/0014-continuous-integration.md). A release is uploaded to Maven Central with the profile `release`, see [ADR 0015](docs/adr/0015-release.md).

The profiles `native` and `native-linux` also build the unwrapper as a native executable: `native` for the system of the build, with the GraalVM that runs Maven, in `oracle-plsql-unwrapper/target/native`, and `native-linux` for Linux x86_64, in a container of GraalVM, in `oracle-plsql-unwrapper/target/native-linux-x64`. The second one needs Docker, and its executable needs glibc 2.34 or later:

```bash
JAVA_HOME=/path/to/graalvm ./mvnw -Pnative,native-linux clean verify
```

The project has three modules, and each builds a JAR in its `target` directory:

- `oracle-plsql-unwrap-spi` — the contract of a wrapped format;
- `oracle-plsql-unwrap-v2` — the format V2, a provider of the service `FormatUnwrapper`; it depends on the SPI only;
- `oracle-plsql-unwrapper` — `Unwrapper` and the command line, with the format V2 only.

The executable JAR with all the classes and the distribution, which holds it with the launchers, are here:

```text
oracle-plsql-unwrapper/target/oracle-plsql-unwrapper-1.0.1-SNAPSHOT-all.jar
oracle-plsql-unwrapper/target/oracle-plsql-unwrapper-1.0.1-SNAPSHOT-dist.zip
oracle-plsql-unwrapper/target/oracle-plsql-unwrapper-1.0.1-SNAPSHOT-dist/unwrapper
```

## Usage

The library is on Maven Central:

```xml
<dependency>
    <groupId>com.github.sftwnd</groupId>
    <artifactId>oracle-plsql-unwrapper</artifactId>
    <version>1.0.0</version>
</dependency>
```

It brings the SPI and the format V2. Each [release on GitHub](https://github.com/sftwnd/oracle-plsql-unwrapper/releases) also holds the jars, the distribution and the native executables for Linux x86_64, Windows x86_64 and macOS (arm64 and x86_64).

The entry point is `com.github.sftwnd.oracle.unwrap.Unwrapper`. The format of each wrapped unit is detected from its header. The formats are services found on the class path or the module path, see [Modules, OSGi and services](#modules-osgi-and-services); this project has `V2`.

To unwrap an SQL script, including all wrapped units it contains:

```java
import com.github.sftwnd.oracle.unwrap.Unwrapper;

String wrappedScript = ...;
String unwrappedScript = new Unwrapper().unwrapScript(wrappedScript);
```

Each wrapped unit, together with its terminating `/` line, is replaced with the restored text. The rest of the script is copied unchanged, including line endings; after a V2 unit a comment line may be added, see [Format V2](#format-v2). If the input ends with the `/` line of the last unit without a line break, the restored script ends with its `/` without a line break too. The restored text uses the line ending of the wrapped unit (CRLF, LF or CR, the one that ends its header line), so the script keeps its line endings, except for a CRLF stored in the text of a V2 unit, see [Format V2](#format-v2); if the header line has none, the line separator of the operating system is used. Line breaks inside string literals follow it too: WRAP for Windows stores them as LF, and in a unit with CRLF they are written as CRLF again.

Converting the line endings of a wrapped file, for example with `core.autocrlf` in git, converts the restored text in the same way and changes nothing else: WRAP stores a line feed inside a literal as `:n` and a carriage return as a single character, and converters leave both alone. To wrap the restored text again, give WRAP the line endings of its platform. WRAP for Linux replaces a CR before LF with a space (10.1–11.2) or keeps it in the text (12.1 and later), so the text restored from a unit wrapped on Windows, or from a file converted to CRLF, has to be converted to LF first. That conversion is safe: WRAP for Windows stores the line breaks of literals as LF anyway.

The restored unit starts with the header of the wrapped unit. WRAP copies the `CREATE` statement from the source up to the end of the unit name: with or without `OR REPLACE`, with the schema, the case, the quotes and the line breaks. WRAP of the same version gives the restored text the same header, see [Format V2](#format-v2).

To read and write SQL files in their original single-byte encoding:

```java
import com.github.sftwnd.oracle.unwrap.Unwrapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

byte[] input = Files.readAllBytes(Paths.get("S.SQL"));
String wrappedScript = new String(input, StandardCharsets.ISO_8859_1);
String unwrappedScript = new Unwrapper().unwrapScript(wrappedScript);
Files.write(Paths.get("S.SQ"), unwrappedScript.getBytes(StandardCharsets.ISO_8859_1));
```

The text of a V2 unit is decoded in the character set the unit names, so a script with V2 units is written in an encoding that covers it, see [Format V2](#format-v2).

Options are passed to the constructor, and `new Unwrapper()` uses `UnwrapOptions.defaults()`. The option `keepStrayCr` keeps a stray CR of a V2 text instead of replacing it with a space, see [Format V2](#format-v2):

```java
import com.github.sftwnd.oracle.unwrap.spi.UnwrapOptions;
import com.github.sftwnd.oracle.unwrap.Unwrapper;

Unwrapper unwrapper = new Unwrapper(UnwrapOptions.defaults().withKeepStrayCr(true));
```

The default of `keepStrayCr` is `false` unless the system property `com.github.sftwnd.oracle.unwrap.keepStrayCr` is `true`: with `-Dcom.github.sftwnd.oracle.unwrap.keepStrayCr=true` every `new Unwrapper()` keeps stray CRs, so an application can switch it without a change of its code. `withKeepStrayCr` sets the option whatever the property says.

A format may have options of its own: `Unwrapper.formatOptions()` lists them, and `UnwrapOptions.withFormatOption(format, name, value)` sets one for one `Unwrapper`, see [ADR 0017](docs/adr/0017-format-options.md). The distribution of this project has none, as V2 has no options of its own.

Other `Unwrapper` operations:

- `unwrap` — unwrap the first wrapped unit;
- `unwrapUnits` — unwrap every wrapped unit and get its format, type and name. A V2 unit has `null` in the PL/SQL version, the PL/SQL compatibility group and `staticSqlAsText`, which only a V1 unit records;
- `supports` — check whether the text contains a wrapped unit of a known format;
- `formats` — the names of the formats this unwrapper restores and what each one covers.

Errors in wrapped content are reported as `UnwrapException` of the package `com.github.sftwnd.oracle.unwrap.spi`.

To compare the result byte by byte with a reference file:

```bash
cmp S.SQ /path/to/U.SQL
```

If `cmp` prints nothing and exits with code 0, the files are identical.

## Command line

The distribution starts the unwrapper from the command line; the script goes from the first file to the second, or from the standard input to the standard output:

```bash
unwrapper/bin/unwrapper S.SQL S.SQ
```

`--help` lists the wrapped formats of this distribution, here `V2` only. `--input-encoding` and `--output-encoding` set the encodings, ISO-8859-1 by default, so the text outside the units stays byte for byte; a restored text that does not fit the output encoding is an error, and nothing is written. `--keep-stray-cr` is the option `keepStrayCr`, see [Format V2](#format-v2). `--format-option <format>.<name>=<value>` sets an option of a format; the help lists such options when a format has them. The exit code is 0, 1 if the script cannot be restored, 2 if the arguments are wrong.

`bin/unwrapper` (`bin/unwrapper.cmd` on Windows) runs the executable JAR on the JDK of `JAVA_HOME`, or on `java` from `PATH`, and takes options of the JVM from `JAVA_OPTS`.

The build also leaves the JARs of the modules in a directory of `target`. On JDK 9 and later the unwrapper starts from them on the module path, where the formats are found through `provides`:

```bash
java --module-path oracle-plsql-unwrapper/target/oracle-plsql-unwrapper-1.0.0-modules --module com.github.sftwnd.oracle.unwrap/com.github.sftwnd.oracle.unwrap.cli.UnwrapperCli --help
```

The executable JAR needs nothing next to it and runs on the class path of any JDK; the distribution has it at its root too:

```bash
java -jar oracle-plsql-unwrapper/target/oracle-plsql-unwrapper-1.0.1-SNAPSHOT-all.jar S.SQL S.SQ
```

## Modules, OSGi and services

The classes are at the level of Java 8. A build on JDK 9 or later adds the JPMS descriptor to each jar in `META-INF/versions/9`, where JDK 8 does not see it. Every jar is an OSGi bundle as well; its bundle symbolic name is the name of its module:

| Artifact                    | JPMS module and bundle                | Exports                               |
|-----------------------------|---------------------------------------|---------------------------------------|
| `oracle-plsql-unwrap-spi`   | `com.github.sftwnd.oracle.unwrap.spi` | `com.github.sftwnd.oracle.unwrap.spi` |
| `oracle-plsql-unwrap-v2`    | `com.github.sftwnd.oracle.unwrap.v2`  | nothing, only the service             |
| `oracle-plsql-unwrapper`    | `com.github.sftwnd.oracle.unwrap`     | `com.github.sftwnd.oracle.unwrap`     |

A format is a provider of the service `com.github.sftwnd.oracle.unwrap.spi.FormatUnwrapper`, and `Unwrapper` finds the providers with `ServiceLoader`: through `META-INF/services` on the class path, through `provides` on the module path, and through the OSGi ServiceLoader Mediator in an OSGi container, which therefore needs a mediator such as Apache Aries SPI Fly. `Unwrapper.formats()` lists what it has found. See [ADR 0011](docs/adr/0011-modules-and-format-services.md) and [ADR 0012](docs/adr/0012-java-8-modules-and-osgi.md).

## Format V2

A V2 unit (Oracle 10g and later) holds the source text as WRAP normalized it, so the restored text is that text, not the original source: unquoted words are in upper case, and comments inside the unit are removed while their line breaks stay. String literals, quoted identifiers and hints keep their case. The restored statement is the header of the wrapped unit followed by the text after the kind and the name of the unit. WRAP copies the TIMESTAMP clause of every kind of unit, with a string literal or `OLD`, and the OID clause of a type (WRAP 10.1: of every kind) into the header and leaves them out of the text (WRAP 10.1 leaves spaces in their place, and the restored statement has the clauses there), and it copies the blanks before the header unless it drops them after a `/` (see below); both are kept. A header without `CREATE`, as in text taken from the data dictionary, gets `CREATE OR REPLACE`; headers with `EDITIONABLE` or `NONEDITIONABLE` are kept as written.

The unit names the character set of its text, the one of `NLS_LANG` when it was wrapped, and the text is decoded in it. A unit wrapped without `NLS_LANG` names US7ASCII whatever its bytes are. Such a text, and a text in a character set the library does not know, is read as UTF-8 if it is valid UTF-8 and as ISO-8859-1 otherwise, which keeps every byte. The restored text may therefore hold characters that the encoding the script was read in cannot hold: write the result in an encoding that covers them, for example the one the units were wrapped in, or UTF-8.

WRAP 12.1 and later keep a `SHARING` clause that follows the unit name in the header and leave it out of the text, and WRAP 23.26.1 does so with `IF NOT EXISTS` between the kind and the name; the restored header has them as written. Units of the kind `ASSEMBLY` (WRAP 11.1 and later) are restored like the other kinds. A unit whose header the library cannot read, for example with a clause of a later release, is reported as `UnwrapException`, so it does not stay wrapped in the restored script. `supports` returns `true` for such a text.

WRAP 12.1 and later for Linux keep the CR of a source with CRLF in the text of the unit, in string literals too, and write the wrapped lines with LF. In a file with LF the text of a unit is restored as it is, with its CRLF, so the value of a literal does not change and these releases give the same unit again.

WRAP 10.2 to 11.2 for Linux replace a CR right before LF with a space, in string literals too, but they read the source in blocks of 512 bytes and do so within a block only: a CR in the last byte of a block, at the offset 511, 1023 and so on of the source file, stays before the LF that starts the next block. A source with CRLF therefore gives a text whose lines end with a blank, with a CRLF on a few of them, at places that depend on the offsets of the source file, comments removed by WRAP included, and not on the program. Such a CR outside string literals and quoted identifiers is stray: by default the restored text has a space in its place, as its other lines have, and a CR inside a literal or a quoted identifier stays as part of its value. A text counts as such if more of its line breaks outside literals have a blank before them than a CR; the CRs that WRAP 12.1 and later keep stand before the line breaks, so their text stays as it is. With `UnwrapOptions.withKeepStrayCr(true)` the text is restored as the unit holds it. Neither text wraps back to the same file as it is: the CRs stand at other offsets in the restored script, and WRAP replaces them there. The text restored with `keepStrayCr` does once a comment before each CR moves it to the last byte of a block again, so such a file is the same up to stray CRs. In the Oracle distributions these are two ORDS files, `ords_migrate` and `upg_oauth`, with 3 and 1 stray CRs, wrapped by WRAP 10.2 or 11.1 from a source with CRLF.

The data of a unit is checked by its SHA-1 digest and its length, and damaged or truncated data is reported as `UnwrapException`. The Base64 lines may be split, joined or have other line endings, as SQL clients and version control leave them.

A removed comment may leave a trace that the next pass of WRAP would lose, so the restored script marks it. A text that ends with a blank or a line break, as it does before a removed comment, gets an empty comment `/**/` after it: every release keeps the text before such a comment and removes the comment with the next line break. After a V2 unit, a line that follows a `/` line and starts with blanks gets a `--` line before it: after the `/` of a unit or of an anonymous block WRAP 10.2 and later keep such blanks only after a comment line, and every release removes that line. The marks cannot be confused with the source: WRAP 10g and later remove every comment from a V2 unit and every `--` line right after a `/` line. So in a script restored from their output, a `/**/` at the end of the text of a V2 unit, before the line break and its `/` line, and a line that holds only `--` right after a `/` line are always marks. Remove them to get the text without the marks; WRAP will then no longer give the same wrapped file from it.

WRAP 10.2.0.5 and 11.1.0.6 give the same wrapped file from the restored text, with one exception: a comment between the unit name and the next word is removed by WRAP without a separator, and the restored text has a space there. Both releases give the same files from the sources checked. WRAP for Linux replaces CR with a space, in string literals too, so the restored text is converted to LF before it is wrapped again.

WRAP 10.1.0.3 gives the same wrapped file too. It keeps more of the source than WRAP 10.2.0.5: the name in the text is in upper case, and the blanks and blank lines before the `/` stay in the text and get the empty comment as well. All these releases write `a000000`, but WRAP 10.1 gives a different text from the same source, so a unit is wrapped again by the release that wrapped it.

WRAP removes `REM` lines, `--` comment lines and empty lines between the statements outside the units, so such a line stands in a wrapped file only if it was added after wrapping, as the `Rem drv:` lines of some Enterprise Manager files are, or the block of `Rem` lines and the empty lines around the statements of the `prvtqopi` and `prvtsqlpatch` files. The restored script keeps it, as all text outside the units, and wrapping the script again gives the file without it. Inside an anonymous block WRAP 10.2 and later keep empty lines and `REM` lines.

What WRAP of each release from 10.1.0.3 to 23.26.1 writes, from the layout of a unit and its header to the line endings and the text outside the units, is described in [WRAP of the format V2: behavior by release](docs/wrap-v2-behavior.md).

## License

The library is distributed under the [BSD 3-Clause License](LICENSE) (BSD-3-Clause): it can be used, changed and redistributed, in open and closed products alike, as long as the copyright notice and the license text are kept. The name of the copyright holder cannot be used to promote derived products without written permission.

The format V1 is a closed module and is not covered by this license.

## Contributing

The project does not accept third-party contributions: pull requests and patches from outside are closed without review.
Bug reports and questions are welcome as issues, but the code is written by the copyright holder only. This keeps all
rights to the code with one holder. The BSD 3-Clause License gives you the right to fork the project and develop the
fork.
