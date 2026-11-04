package dev.ward.infrastructure.analysis.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NodeDependencyEdgesTest {
  private static final Set<String> TYPESCRIPT_EXTENSIONS = Set.of("ts", "tsx", "mts", "cts");
  private static final Set<String> JAVASCRIPT_EXTENSIONS = Set.of("js", "jsx", "mjs", "cjs");

  @Test
  void resolvesExtensionsIndexPathsExportsAndCommonJsOfTheResolutionFixture() throws Exception {
    assertThat(edgesOf("node-resolution"))
        .containsExactlyInAnyOrder(
            "src/app.ts->src/domain/order.ts",
            "src/app.ts->src/util.ts",
            "src/app.ts->src/data/index.ts",
            "src/app.ts->src/types.ts",
            "src/app.ts->packages/lib/src/index.ts",
            "src/app.ts->packages/lib/src/feature/one.ts",
            "src/legacy.js->src/data/index.ts",
            "src/legacy.js->src/lazy.js");
  }

  @Test
  void graphDoesNotDependOnTheOrderOfTheLanguageSupports() throws Exception {
    assertThat(edgesOf("node-resolution", true)).isEqualTo(edgesOf("node-resolution"));
  }

  @Test
  void resolvesCrossPackageImportThroughWorkspaceExportsInTurborepo() throws Exception {
    assertThat(edgesOf("turborepo-workspace"))
        .containsExactly("apps/web/src/index.ts->packages/ui/src/index.ts");
  }

  /** Returns the sorted {@code from->to} edges of the fixture's TypeScript and JavaScript files. */
  static List<String> edgesOf(final String fixture) throws IOException, URISyntaxException {
    return edgesOf(fixture, false);
  }

  /**
   * As {@link #edgesOf(String)}, with the TypeScript and JavaScript supports registered in reverse.
   */
  private static List<String> edgesOf(final String fixture, final boolean reversed)
      throws IOException, URISyntaxException {
    final Path root = fixture(fixture);
    final List<SourceUnit> units = units(root);
    final List<DependencySupport> supports =
        new ArrayList<>(new NodeDependencyAdapters().forProject(root, units));
    if (reversed) {
      Collections.reverse(supports);
    }
    final DependencyAnalysis analysis =
        new DependencyAnalysis(
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.TYPESCRIPT,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX)),
                new FrontendSupport(
                    AnalysisLanguage.JAVASCRIPT,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX))),
            supports);
    return analysis.analyze(units, NO_CACHE).graph().edges().stream()
        .map(edge -> edge.from() + "->" + edge.to())
        .sorted()
        .toList();
  }

  static Path fixture(final String name) throws URISyntaxException {
    return Path.of(NodeDependencyEdgesTest.class.getResource("/fixtures/" + name).toURI());
  }

  /** Every TypeScript and JavaScript file under the fixture, outside installed packages. */
  static List<SourceUnit> units(final Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> !file.toString().contains("node_modules"))
          .flatMap(file -> unit(root, file).stream())
          .sorted((a, b) -> a.path().compareTo(b.path()))
          .toList();
    }
  }

  private static Optional<SourceUnit> unit(final Path root, final Path file) {
    final String name = file.getFileName().toString();
    final String extension = name.substring(name.lastIndexOf('.') + 1);
    final String path = root.relativize(file).toString().replace('\\', '/');
    try {
      if (TYPESCRIPT_EXTENSIONS.contains(extension)) {
        return Optional.of(
            new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, Files.readString(file)));
      }
      if (JAVASCRIPT_EXTENSIONS.contains(extension)) {
        return Optional.of(
            new SourceUnit(path, AnalysisLanguage.JAVASCRIPT, Files.readString(file)));
      }
      return Optional.empty();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final FileSummaryCache NO_CACHE =
      new FileSummaryCache() {
        @Override
        public Optional<FileSummary> read(final SourceUnit unit) {
          return Optional.empty();
        }

        @Override
        public void write(final SourceUnit unit, final FileSummary summary) {}
      };
}
