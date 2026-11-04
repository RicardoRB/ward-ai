package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.swift.SwiftFileSummarizer;
import dev.ward.analysis.domain.swift.SwiftModuleResolver;
import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.analysis.domain.swift.SwiftTarget;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks the cross-module pairs of the graph against the target dependencies the fixtures declare.
 * Edges inside one module, from type-name uses, are not target dependencies and are left out.
 */
class SwiftResolutionReferenceTest {

  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final String ARROW = "->";
  private static final FileSummaryCache NO_SUMMARIES =
      new FileSummaryCache() {
        @Override
        public Optional<FileSummary> read(final SourceUnit unit) {
          return Optional.empty();
        }

        @Override
        public void write(final SourceUnit unit, final FileSummary summary) {
          // nothing is stored: every run summarizes the fixture again
        }
      };

  @Test
  void swiftResolutionEdgesCollapseToItsDeclaredTargetDependencies() throws IOException {
    assertModulePairsMatchDeclared("swift-resolution");
  }

  @Test
  void aNestedTypeUseInsideTheModuleResolvesToItsDeclaringFile() throws IOException {
    final Path root = FIXTURES.resolve("swift-resolution");
    final DependencySnapshot snapshot = analyze(root, SwiftLayouts.read(root));

    assertThat(snapshot.graph().dependenciesOf("Modules/Shop/Checkout.swift"))
        .containsExactly("Modules/Shop/Cart.swift");
  }

  @Test
  void xcodeAppEdgesCollapseToItsProjectTargetDependencies() throws IOException {
    assertModulePairsMatchDeclared("swift-xcode-app");
  }

  @Test
  void classicXcodeProjectEdgesCollapseToItsProjectTargetDependencies() throws IOException {
    assertModulePairsMatchDeclared("swift-xcode-classic");
  }

  private static void assertModulePairsMatchDeclared(final String fixture) throws IOException {
    final Path root = FIXTURES.resolve(fixture);
    final SwiftProjectLayout layout = SwiftLayouts.read(root);
    final DependencySnapshot snapshot = analyze(root, layout);

    final Set<String> resolved =
        snapshot.graph().edges().stream()
            .filter(edge -> !layout.moduleOf(edge.from()).equals(layout.moduleOf(edge.to())))
            .map(edge -> layout.moduleOf(edge.from()) + ARROW + layout.moduleOf(edge.to()))
            .collect(Collectors.toSet());
    final Set<String> declared =
        layout.targets().stream()
            .flatMap(
                (SwiftTarget target) ->
                    target.dependencies().stream()
                        .map(dependency -> target.name() + ARROW + dependency))
            .collect(Collectors.toSet());

    assertThat(resolved).as(fixture).isEqualTo(declared).isNotEmpty();
  }

  private static DependencySnapshot analyze(final Path root, final SwiftProjectLayout layout)
      throws IOException {
    final FrontendSupport frontend =
        new FrontendSupport(
            AnalysisLanguage.SWIFT, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    final DependencySupport support =
        new DependencySupport(
            AnalysisLanguage.SWIFT, new SwiftFileSummarizer(), new SwiftModuleResolver(layout));
    return new DependencyAnalysis(List.of(frontend), List.of(support))
        .analyze(units(root), NO_SUMMARIES);
  }

  private static List<SourceUnit> units(final Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      final List<Path> swift =
          files.filter(file -> file.toString().endsWith(".swift")).sorted().toList();
      final List<SourceUnit> units = new ArrayList<>();
      for (final Path file : swift) {
        final String path = root.relativize(file).toString().replace('\\', '/');
        units.add(new SourceUnit(path, AnalysisLanguage.SWIFT, Files.readString(file)));
      }
      return units;
    }
  }
}
