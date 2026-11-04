package dev.ward.infrastructure.analysis.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.python.PythonProjectLayout;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Compares the Python edges of the {@code ward} graph with the direct imports Import Linter's graph
 * (grimp) builds for each fixture's root packages, which are committed in {@code
 * import-linter-edges.json}. Only edges between modules of those root packages are compared, since
 * that is the graph Import Linter checks.
 */
final class PythonResolutionReferenceTest {

  /** The fixtures that have a committed golden file, with their root packages. */
  static final List<Path> FIXTURES =
      List.of(
          Path.of("src/test/resources/fixtures/python-resolution"),
          Path.of("src/test/resources/fixtures/python-uv-workspace"),
          Path.of("src/test/resources/fixtures/python-matrix/basic-uv"),
          Path.of("src/test/resources/fixtures/python-matrix/basic-poetry"),
          Path.of("src/test/resources/fixtures/python-matrix/basic-pip"));

  private static final String GOLDEN = "import-linter-edges.json";
  private static final String PY = ".py";
  private static final String INIT = "__init__";
  private static final String SEPARATOR = "/";

  @Test
  void pythonFixtureEdgesEqualImportLinterGraph() throws IOException {
    for (final Path fixture : FIXTURES) {
      final String golden = Files.readString(fixture.resolve(GOLDEN));
      final Set<String> expected = GoldenEdges.edges(golden);
      final List<String> roots = GoldenEdges.roots(golden);

      assertThat(wardEdges(fixture, roots)).as("edges of %s", fixture).isEqualTo(expected);
    }
  }

  /** The edges of the ward graph between root-package modules, as {@code from -> to} keys. */
  private static Set<String> wardEdges(final Path fixture, final List<String> roots)
      throws IOException {
    final PythonProjectLayout layout = PythonLayouts.read(fixture);
    final List<SourceUnit> units = pythonUnits(fixture);
    final DependencySupport python =
        new PythonDependencyAdapters().forProject(fixture, units).get(0);
    final List<DependencyEdge> edges = python.resolver().resolve(summaries(units, python));
    final Set<String> keys = new TreeSet<>();
    for (final DependencyEdge edge : edges) {
      if (inRoots(edge.from(), layout, roots) && inRoots(edge.to(), layout, roots)) {
        keys.add(GoldenEdges.key(edge.from(), edge.to()));
      }
    }
    return keys;
  }

  /** The Python sources of the fixture as analysis units, with paths relative to the fixture. */
  private static List<SourceUnit> pythonUnits(final Path fixture) throws IOException {
    return pythonFiles(fixture).stream()
        .map(
            file ->
                new SourceUnit(
                    fixture.relativize(file).toString().replace('\\', '/'),
                    AnalysisLanguage.PYTHON,
                    read(file)))
        .toList();
  }

  /** Summarizes every unit, as the analysis does for a project. */
  private static List<FileSummary> summaries(
      final List<SourceUnit> units, final DependencySupport python) {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    return units.stream()
        .map(unit -> python.summarizer().summarize(unit, frontend.parse(unit).tree()))
        .toList();
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException exception) {
      throw new UncheckedIOException("cannot read fixture " + file, exception);
    }
  }

  private static List<Path> pythonFiles(final Path fixture) throws IOException {
    try (Stream<Path> files = Files.walk(fixture)) {
      return files
          .filter(file -> file.toString().endsWith(PY))
          .filter(file -> !file.toString().contains("/.venv/"))
          .filter(file -> !file.toString().contains("__pycache__"))
          .sorted()
          .toList();
    }
  }

  /** Whether a file's module is in one of the root packages, found through its deepest root. */
  private static boolean inRoots(
      final String path, final PythonProjectLayout layout, final List<String> roots) {
    return topPackage(path, layout).filter(roots::contains).isPresent();
  }

  private static Optional<String> topPackage(final String path, final PythonProjectLayout layout) {
    final Optional<String> root =
        layout.sourceRoots().stream()
            .filter(path::startsWith)
            .max((left, right) -> Integer.compare(left.length(), right.length()));
    if (root.isEmpty() || !path.endsWith(PY)) {
      return Optional.empty();
    }
    final String relative = path.substring(root.get().length(), path.length() - PY.length());
    final String module = relative.endsWith(INIT) ? parentOf(relative) : relative;
    final String first = module.split(SEPARATOR, -1)[0];
    return first.isEmpty() ? Optional.empty() : Optional.of(first);
  }

  private static String parentOf(final String relative) {
    final int slash = relative.lastIndexOf(SEPARATOR);
    return slash < 0 ? "" : relative.substring(0, slash);
  }
}
