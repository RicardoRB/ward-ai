package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * One pinned open-source Java project (Apache-2.0), read from its extracted source archive. The
 * source is never part of the repository and is only read: nothing in it is installed or run.
 *
 * @param name the short name that starts every path of the report
 * @param pin the tag or commit the committed report measures
 * @param archiveSha256 the SHA-256 of the GitHub source archive ({@code .tar.gz}) of that pin
 * @param folder the folder the archive extracts to
 */
record JavaCorpus(String name, String pin, String archiveSha256, String folder) {
  /** Apache Commons Lang, a library in Java 8 syntax. */
  static final JavaCorpus COMMONS_LANG =
      new JavaCorpus(
          "commons-lang",
          "rel/commons-lang-3.22.0",
          "21bf0c5913227cef8f1c271bfb3b556440797126448e000073ba88ac4a1fb587",
          "commons-lang-rel-commons-lang-3.22.0"
      );

  /** Spring PetClinic, an application in Java 17 syntax; the project has no tags. */
  static final JavaCorpus PETCLINIC =
      new JavaCorpus(
          "spring-petclinic",
          "500158f732419217507c7656904b8e6aa1bcc0d6",
          "4932ae0209375b3cca3dd7c8f6f9345498d75dedc5970dcfa91814f1b830768d",
          "spring-petclinic-500158f732419217507c7656904b8e6aa1bcc0d6"
      );

  private static final String JAVA = ".java";

  /** Returns every corpus, in report order. */
  static List<JavaCorpus> all() {
    return List.of(COMMONS_LANG, PETCLINIC);
  }

  /**
   * Returns the Java sources below this corpus's folder in {@code directory}, in path order; a path
   * is {@code <name>/<path in the archive>}.
   */
  List<SourceUnit> units(final Path directory) throws IOException {
    final Path root = directory.resolve(folder);
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(JAVA))
          .sorted()
          .map(file -> unit(root, file))
          .toList();
    }
  }

  private SourceUnit unit(final Path root, final Path file) {
    try {
      return new SourceUnit(
          name + "/" + root.relativize(file).toString().replace('\\', '/'),
          AnalysisLanguage.JAVA,
          Files.readString(file, StandardCharsets.UTF_8)
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
