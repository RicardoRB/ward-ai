package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.python.PythonFileSummarizer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The source of Healthchecks, a Django application (BSD-3-Clause), read from an extracted release
 * archive. The source is never part of the repository and is only read: nothing in it is installed
 * or run.
 */
final class PythonCorpus {
  /** The release the committed report measures. */
  static final String TAG = "v5.0.3";

  /** The SHA-256 of the source archive {@code healthchecks-v5.0.3.tar.gz} of that tag. */
  static final String ARCHIVE_SHA256 =
      "ee1b93bf0719f948a8693ac06c7d571394fb65723d0faf21cd0374721afa964e";

  private static final String EXTENSION = ".py";

  private PythonCorpus() {}

  /** Returns every Python source below {@code root}, with root-relative paths, in path order. */
  static List<SourceUnit> units(final Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(EXTENSION))
          .sorted()
          .map(file -> unit(root, file))
          .toList();
    }
  }

  private static SourceUnit unit(final Path root, final Path file) {
    try {
      return new SourceUnit(
          root.relativize(file).toString().replace('\\', '/'),
          AnalysisLanguage.PYTHON,
          Files.readString(file, StandardCharsets.UTF_8)
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Returns the summary of each unit, which the unused-definition rules read. */
  static Map<String, FileSummary> summaries(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends
  ) {
    final LanguageFrontend frontend =
        frontends.stream()
            .filter(candidate -> candidate.language() == AnalysisLanguage.PYTHON)
            .findFirst()
            .orElseThrow()
            .frontend();
    final PythonFileSummarizer summarizer = new PythonFileSummarizer();
    final Map<String, FileSummary> summaries = new LinkedHashMap<>();
    for (final SourceUnit unit : units) {
      summaries.put(unit.path(), summarizer.summarize(unit, frontend.parse(unit).tree()));
    }
    return summaries;
  }
}
