package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The sources of one language below a directory, for the smoke tests over extracted corpora. */
final class CorpusSources {
  private CorpusSources() {}

  /** The sources of the language below {@code directory}, in path order, by relative path. */
  static List<SourceUnit> units(final Path directory, final AnalysisLanguage language)
      throws IOException {
    final String extension = language == AnalysisLanguage.KOTLIN ? ".kt" : ".java";
    try (Stream<Path> files = Files.walk(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(extension))
          .sorted()
          .map(file -> unit(directory, file, language))
          .toList();
    }
  }

  private static SourceUnit unit(
      final Path root,
      final Path file,
      final AnalysisLanguage language
  ) {
    try {
      return new SourceUnit(
          root.relativize(file).toString().replace('\\', '/'),
          language,
          Files.readString(file, StandardCharsets.UTF_8)
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
