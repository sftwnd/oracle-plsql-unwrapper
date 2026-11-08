# 0012. Java 8 level with JPMS modules and OSGi bundles

[Русская версия](0012-java-8-modules-and-osgi_ru.md)

## Context

The library is run where old Oracle installations live, so it has to work on Java 8. At the same time an application on
JDK 9 and later should get real modules with closed internals, and an OSGi container real bundles. The formats are
services (ADR 0011), so each of the three ways to run has to find them.

## Decision

- All the code and the tests are at the level of Java 8, in the language and in the API: JDK 9 and later compile with
  `release 8`, which checks the API too. JDK 8 compiles with `source` and `target`.
- `src/main/java9/module-info.java` of a module is compiled by the profile `java9-modules`, active on JDK 9 and later,
  into `META-INF/versions/9`, with `release 9`, and the manifest says `Multi-Release: true`. The compilation runs in the
  phase `prepare-package`: a descriptor in `META-INF/versions/9` is not found in `target/classes`, and in this phase the
  reactor gives the jars of the required modules. The classes of the module reach javac with `--patch-module`.
- On JDK 8 the same build makes jars without a descriptor. Their manifest has `Automatic-Module-Name`, so they work as
  automatic modules with the same names.
- `maven-bundle-plugin` builds the OSGi headers of every module in the phase `process-classes`. Only the SPI and the API
  packages are exported. The formats are bound with the OSGi ServiceLoader Mediator: a format has
  `Provide-Capability: osgi.serviceloader` and requires the extender `osgi.serviceloader.registrar`, the unwrapper
  requires `osgi.serviceloader.processor` and the service. Apache Aries SPI Fly is such a mediator.
- The distribution of an unwrapper is `target/<artifact>-<version>-dist.zip`, with the same content in the directory
  `target/<artifact>-<version>-dist`: `LICENSE`, the executable jar and `bin/unwrapper`, `bin/unwrapper.cmd`, which run it.
- The jars of the modules stay in `target/<artifact>-<version>-modules`, for a start on the module path and for `jlink`.
- Next to its ordinary jar an unwrapper has an executable jar with all the classes,
  `target/<artifact>-<version>-all.jar`, for `java -jar` on any JDK. `maven-assembly-plugin` builds it from
  `src/assembly/executable-jar.xml` of the module `oracle-plsql-unwrapper`: the manifest is new, the providers in
  `META-INF/services` are merged, and `META-INF/LICENSE` is the one of the jar of the unwrapper, as it is. The SPI and
  the formats, under the same license, leave theirs out. The handler `file-aggregator` of `maven-assembly-plugin` is not
  used for the license: it writes the time of the build into the file, and the executable jar and the distribution
  would differ with every build. `UnwrapperLaunchIT` checks that the license text of both is the file `LICENSE`.
- `UnwrapperLaunchIT` starts the built distribution and the executable jar in a JVM of their own in each of these ways.

## Rationale

- A multi-release descriptor is invisible to Java 8 and to old tools that read every class of a jar, unlike
  `module-info.class` at the root of the jar.
- `release` catches a call of a later API at compile time. With `source` and `target` alone a build on a later JDK would
  pass and fail on Java 8 at run time.
- The profile by the JDK leaves the build working on JDK 8, where `module-info.java` cannot be compiled.
- The manifest is built before the descriptor exists, so bnd takes `osgi.ee` from the classes of Java 8.
- The ServiceLoader Mediator lets one provider serve all three ways: the code has no OSGi types and no dependency on
  them, and `META-INF/services` stays the only list of providers.
- A distribution of one jar is the simplest thing to hand over, and a start from a console gets nothing from modules: the
  result is the same. Modules matter to an application that embeds the library.
- One file is the simplest way to run the tool, while the ordinary jars stay modules and bundles and the module
  `oracle-plsql-unwrapper-pro` depends on them.
- The manifest of a part names its bundle, its module and the jars next to it. In an executable jar none of that is true.

## Alternatives

- **A higher level of Java with a separate build for Java 8.** Two versions of the code to keep in step.
- **`module-info.class` at the root of the jar.** Old class scanners on Java 8 fail on a class of version 53.
- **`moditect-maven-plugin`.** It adds the descriptor to a built jar, but javac does not check it and the build gets one
  more plugin.
- **`bnd` generating the descriptor with `-jpms-module-info`.** The descriptor would be derived, not written, and the
  `provides` clause is exactly what has to be visible.
- **Declarative Services or a `BundleActivator`.** The code of a format would depend on OSGi.
- **Only an executable jar with all the classes.** It cannot hold several modules or bundles, and
  `oracle-plsql-unwrapper-pro` needs the ordinary jar.
- **The jars of the modules in the distribution, with a launcher that picks the module path.** More files for the same
  result.
- **`maven-shade-plugin`.** It keeps the manifest of the main jar, so the result claims to be a bundle and a module.

## Consequences

- No records, no `switch` expressions, no `var`, no `List.of`, and no API after Java 8. Value classes are written by hand.
- `./mvnw compile` does not compile the descriptors, `package` and `verify` do.
- A wrong descriptor is found by the build on JDK 9 and later only; the tests on the module path cover it.
- A jar built on JDK 8 has no descriptor: for closed internals build on JDK 9 or later.
- The executable jar runs on the class path only: it has no descriptor and no OSGi headers.
- `jlink` builds a runtime image of the unwrapper from `target/<artifact>-<version>-modules`. It has to add `jdk.charsets`,
  which the character sets of V2 need. The build does not do it: an image is made for one platform.
- The profiles `native` and `native-linux` of the parent build an executable of an unwrapper from its executable jar with
  `native-image`, in each module with `native.skip=false`: `oracle-plsql-unwrapper` here and `oracle-plsql-unwrapper-pro` in
  the project of the same name. They build for the system of the build, and for Linux x86_64 in a container of GraalVM on
  Oracle Linux 10, whose executable needs glibc 2.34. `native-image` builds only for the system it runs on.
- A format brings the options of the image in `META-INF/native-image` of its jar: V2 asks for `-H:+AddAllCharsets`, the
  character sets of its texts. `native-image` reads them from every jar, so any build of an image gets them, and it finds
  the providers in `META-INF/services` itself.
- In OSGi an application needs a mediator in the container, or the bundle of the unwrapper does not resolve.
- JDK 25 still compiles `release 8`, with a warning; a later JDK that drops it will need a toolchain.
