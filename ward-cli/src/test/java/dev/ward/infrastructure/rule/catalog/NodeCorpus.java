package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The TypeScript and JavaScript sources of a measured corpus, read from an extracted source
 * archive. The source is never part of the repository and is only read: nothing in it is installed
 * or run.
 */
final class NodeCorpus {
  private static final Set<String> TYPESCRIPT = Set.of(".ts", ".tsx", ".mts", ".cts");
  private static final Set<String> JAVASCRIPT = Set.of(".js", ".jsx", ".mjs", ".cjs");
  private static final String DECLARATIONS = ".d.ts";
  private static final String DEPENDENCIES = "node_modules";

  /** The folders and minified scripts that the gate's source reader skips too. */
  private static final Set<String> SKIPPED_FOLDERS = Set.of("target", "build", "dist", "mutants");

  private static final Pattern MINIFIED = Pattern.compile("\\.min\\.[cm]?js$");

  private final Path root;

  /** Reads the sources below {@code root}, the directory the archive was extracted to. */
  NodeCorpus(final Path root) {
    this.root = root;
  }

  /** Returns the TypeScript and JavaScript sources, with root-relative paths, in path order. */
  List<SourceUnit> units() throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> !root.relativize(file).toString().contains(DEPENDENCIES))
          .filter(file -> !file.toString().endsWith(DECLARATIONS))
          .filter(file -> !MINIFIED.matcher(file.getFileName().toString()).find())
          .filter(file -> !skippedFolder(file))
          .filter(file -> language(file) != null)
          .sorted()
          .map(this::unit)
          .toList();
    }
  }

  /** The dependencies of the units, so that the taint rules follow calls into other files. */
  DependencySnapshot snapshot(final List<SourceUnit> units, final List<FrontendSupport> frontends) {
    return new DependencyAnalysis(frontends, new NodeDependencyAdapters().forProject(root, units))
        .analyze(units, new NoSummaries());
  }

  private boolean skippedFolder(final Path file) {
    for (final Path part : root.relativize(file.getParent())) {
      if (SKIPPED_FOLDERS.contains(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private SourceUnit unit(final Path file) {
    try {
      return new SourceUnit(
          root.relativize(file).toString().replace('\\', '/'),
          language(file),
          Files.readString(file, StandardCharsets.UTF_8)
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static AnalysisLanguage language(final Path file) {
    final String name = file.getFileName().toString();
    final String extension = name.substring(name.lastIndexOf('.') + 1);
    if (TYPESCRIPT.contains("." + extension)) {
      return AnalysisLanguage.TYPESCRIPT;
    }
    return JAVASCRIPT.contains("." + extension) ? AnalysisLanguage.JAVASCRIPT : null;
  }

  /** Keeps no summaries, so the run resolves the project once. */
  private static final class NoSummaries implements FileSummaryCache {
    @Override
    public java.util.Optional<FileSummary> read(final SourceUnit unit) {
      return java.util.Optional.empty();
    }

    @Override
    public void write(final SourceUnit unit, final FileSummary summary) {
      // nothing is kept
    }
  }
}
