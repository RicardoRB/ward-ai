package dev.ward.infrastructure.analysis.python;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regenerates each fixture's direct imports with a real grimp, the graph Import Linter checks, and
 * compares them with the committed {@code import-linter-edges.json}. It is skipped unless {@code
 * -Dward.python.importlinter} names a Python with {@code import-linter} installed, for example:
 * {@code ./mvnw -Dtest=ImportLinterEdgesSmokeTest -Dward.python.importlinter=.venv/bin/python
 * test}.
 */
@EnabledIfSystemProperty(named = "ward.python.importlinter", matches = ".+")
final class ImportLinterEdgesSmokeTest {

  private static final String SCRIPT = "/import-linter/grimp_edges.py";
  private static final String GOLDEN = "import-linter-edges.json";
  private static final long TIMEOUT_SECONDS = 120;

  @TempDir Path work;

  @Test
  void directImportsEqualTheCommittedGoldenFiles() throws IOException, InterruptedException {
    final Path script = scriptFile();
    for (final Path fixture : PythonResolutionReferenceTest.FIXTURES) {
      final String golden = Files.readString(fixture.resolve(GOLDEN));
      final List<String> roots = GoldenEdges.roots(golden);
      final Set<String> expected = GoldenEdges.edges(golden);

      assertThat(grimpEdges(script, fixture, roots)).as("edges of %s", fixture).isEqualTo(expected);
    }
  }

  /** Runs the script with the fixture's roots and source roots and returns the edges it prints. */
  private Set<String> grimpEdges(final Path script, final Path fixture, final List<String> roots)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                System.getProperty("ward.python.importlinter"),
                script.toString(),
                fixture.toAbsolutePath().toString(),
                String.join(",", roots),
                sourceRoots(fixture))
            .directory(work.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).as("grimp script exit code").isZero();
    return GoldenEdges.edges(output);
  }

  /** The source directories on the path, which are the layout's roots without the separator. */
  private static String sourceRoots(final Path fixture) {
    return PythonLayouts.read(fixture).sourceRoots().stream()
        .map(root -> root.replaceFirst("/$", ""))
        .collect(Collectors.joining(","));
  }

  private Path scriptFile() throws IOException {
    final Path file = work.resolve("grimp_edges.py");
    try (InputStream stream = ImportLinterEdgesSmokeTest.class.getResourceAsStream(SCRIPT)) {
      Files.write(file, stream.readAllBytes());
    }
    return file;
  }
}
