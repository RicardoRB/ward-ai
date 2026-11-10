package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Runs the redundant-public-accessibility rule on a two-target SwiftPM fixture through the real
 * module layout: a public declaration only its own target uses is reported, while a use from
 * another target or from a file in no target keeps it.
 */
class SwiftRedundantAccessibilityTest {

  private static final Path FIXTURE =
      Path.of("src/test/resources/fixtures/swift-redundant-accessibility");
  private static final String RULE_ID = "ward:swift/redundant-public-accessibility";
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
  void aPublicDeclarationOnlyItsOwnTargetUsesIsReportedAndOthersAreNot() throws IOException {
    final List<AnalysisFinding> findings = findings(FIXTURE);

    assertThat(findings)
        .extracting(AnalysisFinding::file)
        .containsExactlyInAnyOrder("Sources/Core/Cart.swift", "Sources/Core/Helper.swift");
    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .anySatisfy(message -> assertThat(message).contains("'total'").contains("'Core'"));
  }

  /** The findings of the rule, with the modules the real SwiftPM layout gives each file. */
  private static List<AnalysisFinding> findings(final Path root) throws IOException {
    final List<SourceUnit> units = units(root);
    final List<DependencySupport> supports = new SwiftDependencyAdapters().forProject(root, units);
    final DependencySnapshot snapshot =
        new DependencyAnalysis(frontends(), supports).analyze(units, NO_SUMMARIES);
    final RuleDefinition rule =
        new RuleDefinition(
            RULE_ID,
            Set.of(AnalysisLanguage.SWIFT),
            RuleCapability.SYMBOLS,
            AnalysisSeverity.WARNING,
            "check = \"swift-unused-definition\"");
    return UnusedDefinitionFindings.findings(
        List.of(rule), snapshot.summaries(), moduleOf(supports));
  }

  private static Function<String, Optional<String>> moduleOf(
      final List<DependencySupport> supports) {
    return path ->
        supports.stream()
            .map(support -> support.resolver().moduleOf(path))
            .flatMap(Optional::stream)
            .findFirst();
  }

  private static List<FrontendSupport> frontends() {
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.SWIFT, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX)));
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
