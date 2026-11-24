package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One pinned open-source project (Apache-2.0, MIT or Unlicense), read from its extracted source archive. The
 * source is never part of the repository and is only read: nothing in it is installed or run.
 *
 * @param name the short name that starts every path of the report
 * @param pin the tag or commit the committed report measures
 * @param archiveSha256 the SHA-256 of the GitHub source archive ({@code .tar.gz}) of that pin
 * @param folder the folder the archive extracts to
 * @param language the language whose sources are read
 */
record PinnedCorpus(
    String name,
    String pin,
    String archiveSha256,
    String folder,
    AnalysisLanguage language
) {
  /** Apache Commons Lang, a library in Java 8 syntax. */
  static final PinnedCorpus COMMONS_LANG =
      new PinnedCorpus(
          "commons-lang",
          "rel/commons-lang-3.22.0",
          "21bf0c5913227cef8f1c271bfb3b556440797126448e000073ba88ac4a1fb587",
          "commons-lang-rel-commons-lang-3.22.0",
          AnalysisLanguage.JAVA
      );

  /** Spring PetClinic, an application in Java 17 syntax; the project has no tags. */
  static final PinnedCorpus PETCLINIC =
      new PinnedCorpus(
          "spring-petclinic",
          "500158f732419217507c7656904b8e6aa1bcc0d6",
          "4932ae0209375b3cca3dd7c8f6f9345498d75dedc5970dcfa91814f1b830768d",
          "spring-petclinic-500158f732419217507c7656904b8e6aa1bcc0d6",
          AnalysisLanguage.JAVA
      );

  /** Spring Data Examples, Spring Boot applications with records, REST and security samples. */
  static final PinnedCorpus SPRING_DATA_EXAMPLES =
      new PinnedCorpus(
          "spring-data-examples",
          "7747029e6157cb780862826b6ae87c88d7a4df3c",
          "3d25281914e28550d1a1d2518564b84ae3e071c5aee7a5a920e0daa7ba7edc81",
          "spring-data-examples-7747029e6157cb780862826b6ae87c88d7a4df3c",
          AnalysisLanguage.JAVA
      );

  /** Okio, a multiplatform library with expect and actual declarations. */
  static final PinnedCorpus OKIO =
      new PinnedCorpus(
          "okio",
          "parent-3.18.2",
          "d327ed5e1074a7f9ddd2bc5e4230040164aa4a4c916e033d2a2ba66bd19023b9",
          "okio-parent-3.18.2",
          AnalysisLanguage.KOTLIN
      );

  /** Spring PetClinic in Kotlin, a Spring Boot application. */
  static final PinnedCorpus PETCLINIC_KOTLIN =
      new PinnedCorpus(
          "spring-petclinic-kotlin",
          "v3.2.0",
          "bb9843e7767534ee29c0979aac482b7e4dc268aee28f52d555ae8d2f5aa81e0b",
          "spring-petclinic-kotlin-3.2.0",
          AnalysisLanguage.KOTLIN
      );

  /** Newtonsoft.Json, a library in C# 12 syntax with a large legacy surface (MIT). */
  static final PinnedCorpus NEWTONSOFT_JSON =
      new PinnedCorpus(
          "newtonsoft-json",
          "13.0.4",
          "71fb8eda5ee37ab3405ea5df6c2a9490716e411db82e6c0878132bdf29aedb17",
          "Newtonsoft.Json-13.0.4",
          AnalysisLanguage.CSHARP
      );

  /** eShop, a modern .NET application with records, primary constructors and minimal APIs (MIT). */
  static final PinnedCorpus ESHOP =
      new PinnedCorpus(
          "eshop",
          "ab10e4c68fd26369578ebfb3fd1ccf3b867d0444",
          "e9eb4454c1fcb46dc39f425d0d510ab242af5cddf53570d7de47da9bcb68438c",
          "eShop-ab10e4c68fd26369578ebfb3fd1ccf3b867d0444",
          AnalysisLanguage.CSHARP
      );

  /** RuoYi, a layered Spring Boot admin application (controller, service, mapper) with file download, upload and HTTP calls (MIT). */
  static final PinnedCorpus RUOYI =
      new PinnedCorpus(
          "ruoyi",
          "v4.8.3",
          "730d8259046b076ed653463e44ee028109aa4e09a87e4d3e503958e6cad6c974",
          "RuoYi-4.8.3",
          AnalysisLanguage.JAVA
      );

  /** Mall, a layered Spring Boot e-commerce service with an admin and a portal application (Apache-2.0). */
  static final PinnedCorpus MALL =
      new PinnedCorpus(
          "mall",
          "v1.0.3",
          "df0a916c111d1a20ceb74569cb3168327ca48245d06d1e950b5a5aec526770b8",
          "mall-1.0.3",
          AnalysisLanguage.JAVA
      );

  /** Eladmin, a layered Spring Boot admin service with generated code, file and mail endpoints (Apache-2.0). */
  static final PinnedCorpus ELADMIN =
      new PinnedCorpus(
          "eladmin",
          "v2.7",
          "31916a16e0ddfcbc589ec7b4722b90b3b1993d568f3d3b155918d94ab77497e4",
          "eladmin-2.7",
          AnalysisLanguage.JAVA
      );

  /** Ktor samples, small Kotlin web applications and services (Apache-2.0); the project has no tags. */
  static final PinnedCorpus KTOR_SAMPLES =
      new PinnedCorpus(
          "ktor-samples",
          "e9763d69954723094da05c3d57b34755b9a1359a",
          "61f55b3914e47d099127f1832e4b451d2861cbc9ee9057791460a7266fa56c18",
          "ktor-samples-e9763d69954723094da05c3d57b34755b9a1359a",
          AnalysisLanguage.KOTLIN
      );

  /** Returns the C# corpora, in report order. */
  static List<PinnedCorpus> csharp() {
    return List.of(NEWTONSOFT_JSON, ESHOP);
  }

  /** ripgrep, a command-line search tool and its library crates (MIT or Unlicense). */
  static final PinnedCorpus RIPGREP =
      new PinnedCorpus(
          "ripgrep",
          "15.2.0",
          "7605249d3eb0d5f170e3414498e3344e26b1e7a147aec518b57090b80036a562",
          "ripgrep-15.2.0",
          AnalysisLanguage.RUST
      );

  /** Tokio, an asynchronous runtime with unsafe code, macros and platform modules (MIT). */
  static final PinnedCorpus TOKIO =
      new PinnedCorpus(
          "tokio",
          "tokio-1.54.1",
          "3d84bf16b8fdbb19b6455dfa5971cead60e0ddbd9ee1aa368a4b4206998f9bdd",
          "tokio-tokio-1.54.1",
          AnalysisLanguage.RUST
      );

  /** The full-stack FastAPI template, a FastAPI service with SQLModel and JWT login (MIT). */
  static final PinnedCorpus FASTAPI_TEMPLATE =
      new PinnedCorpus(
          "fastapi-template",
          "0.12.0",
          "5083f1af1a64788bedbfceeca93889b3290f5edacbd6f27c3da2d80c9db5bf98",
          "full-stack-fastapi-template-0.12.0",
          AnalysisLanguage.PYTHON
      );

  /** NestJS, a TypeScript server framework with its integration samples (MIT). */
  static final PinnedCorpus NEST =
      new PinnedCorpus(
          "nest",
          "v12.6.0",
          "ad6a1244f13cf6c08252366bbaf292de5997bede5e96c2a747d05f3a6a108e03",
          "nest-12.6.0",
          AnalysisLanguage.TYPESCRIPT
      );

  /** Vapor, a server-side Swift web framework (MIT). */
  static final PinnedCorpus VAPOR =
      new PinnedCorpus(
          "vapor",
          "4.122.4",
          "26be73ba4f715f58ade6640088942c47c47f6f5376807d89fb639b56e21366f5",
          "vapor-4.122.4",
          AnalysisLanguage.SWIFT
      );

  /** Returns the corpora of the differential run of {@code key}, the name of a language group. */
  static List<PinnedCorpus> forLanguage(final String key) {
    final Map<String, List<PinnedCorpus>> byKey =
        Map.of(
            "java",
            java(),
            "kotlin",
            kotlin(),
            "csharp",
            csharp(),
            "rust",
            rust(),
            "python",
            python(),
            "node",
            node(),
            "swift",
            swift()
        );
    final List<PinnedCorpus> found = byKey.get(key);
    if (found == null) {
      throw new IllegalArgumentException("no differential run for " + key);
    }
    return found;
  }

  /** Returns the Python corpora of the differential run. */
  static List<PinnedCorpus> python() {
    return List.of(FASTAPI_TEMPLATE);
  }

  /** Returns the TypeScript and JavaScript corpora of the differential run. */
  static List<PinnedCorpus> node() {
    return List.of(NEST);
  }

  /** Returns the Swift corpora of the differential run. */
  static List<PinnedCorpus> swift() {
    return List.of(VAPOR);
  }

  /** Returns whether the corpora of this language are read by a rule that covers {@code rule}. */
  boolean readBy(final dev.ward.analysis.domain.rule.RuleDefinition rule) {
    return rule.covers(language)
        || language == AnalysisLanguage.TYPESCRIPT && rule.covers(AnalysisLanguage.JAVASCRIPT);
  }

  /** Returns the Java corpora, in report order. */
  static List<PinnedCorpus> java() {
    return List.of(COMMONS_LANG, PETCLINIC, SPRING_DATA_EXAMPLES, RUOYI, MALL, ELADMIN);
  }

  /** Returns the Kotlin corpora, in report order. */
  static List<PinnedCorpus> kotlin() {
    return List.of(OKIO, PETCLINIC_KOTLIN, KTOR_SAMPLES);
  }

  /** Returns the Rust corpora, in report order. */
  static List<PinnedCorpus> rust() {
    return List.of(RIPGREP, TOKIO);
  }

  /** Returns the extension of the sources of this corpus's language. */
  String extension() {
    return switch (language) {
      case KOTLIN -> ".kt";
      case CSHARP -> ".cs";
      case RUST -> ".rs";
      case PYTHON -> ".py";
      case SWIFT -> ".swift";
      case TYPESCRIPT -> ".ts";
      default -> ".java";
    };
  }

  /**
   * Returns the sources below this corpus's folder in {@code directory}, in path order; a path
   * is {@code <name>/<path in the archive>}.
   */
  List<SourceUnit> units(final Path directory) throws IOException {
    final Path root = directory.resolve(folder);
    if (language == AnalysisLanguage.TYPESCRIPT) {
      return new NodeCorpus(root).units().stream()
          .map(unit -> new SourceUnit(name + "/" + unit.path(), unit.language(), unit.text()))
          .toList();
    }
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(extension()))
          .sorted()
          .map(file -> unit(root, file))
          .toList();
    }
  }

  private SourceUnit unit(final Path root, final Path file) {
    try {
      return new SourceUnit(
          name + "/" + root.relativize(file).toString().replace('\\', '/'),
          language,
          Files.readString(file, StandardCharsets.UTF_8)
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
