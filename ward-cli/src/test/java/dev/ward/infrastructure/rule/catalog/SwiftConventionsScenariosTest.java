package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story #596: Swift rules follow Swift conventions. */
class SwiftConventionsScenariosTest {
  private static final String SECURITY = "security";
  private static final List<String> DECIDED =
      List.of(
          "unused-imports",
          "hidden-field",
          "constant-name",
          "method-type-parameter-name",
          "class-type-parameter-name",
          "missing-switch-default",
          "naming",
          "custom-import-order",
          "large-tuple",
          "google-right-curly",
          "for-where",
          "hard-coded-credential"
      );

  /** Runs the Swift rules of the standard preset, and the security rules, over one source. */
  private static List<AnalysisFinding> standardPreset(final String source) {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.SWIFT))
            .filter(
                rule ->
                    RulePreset.STANDARD.selects(rule.presets()) || SECURITY.equals(rule.category())
            )
            .toList();
    return new RuleEngine(BundledRuleMatchers.create())
        .run(
            List.of(new SourceUnit("Sample.swift", AnalysisLanguage.SWIFT, source)),
            new RuleFixtureRunner().frontends(),
            rules
        )
        .findings();
  }

  private static List<String> reported(final String ruleName, final String source) {
    return standardPreset(source).stream()
        .filter(finding -> finding.ruleId().equals("ward:swift/" + ruleName))
        .map(finding -> finding.line() + ":" + finding.column())
        .toList();
  }

  @Test
  @DisplayName("a Swift idiom is not a finding")
  void aSwiftIdiomIsNotAFinding() {
    // Given init(value:) { self.value = value }
    final String source =
        """
        public struct Box {
          public let value: Int
          public init(value: Int) { self.value = value }
        }
        """;

    // When the standard Swift preset runs
    final List<String> found = reported("hidden-field", source);

    // Then hidden-field reports nothing
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("an exhaustive switch")
  void anExhaustiveSwitch() {
    // Given a Swift switch that covers every case of an enum without default
    final String source =
        """
        enum Light { case red, green }
        func name(_ light: Light) -> String {
          switch light {
          case .red: return "red"
          case .green: return "green"
          }
        }
        """;

    // When the Swift preset runs
    final List<String> found = reported("missing-switch-default", source);

    // Then missing-switch-default reports nothing
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("two credentials in one declaration")
  void twoCredentialsInOneDeclaration() {
    // Given let user = "user", password = "password"
    final String source = "let user = \"user\", password = \"password\"\n";

    // When hard-coded-credential runs
    final List<String> found = reported("hard-coded-credential", source);

    // Then it reports the password constant
    assertThat(found).containsExactly("1:" + (source.indexOf("password") + 1));
  }

  @Test
  @DisplayName("the corpus labels are clean")
  void theCorpusLabelsAreClean() throws IOException {
    // Given the regenerated report and labels of #567
    final List<String> labels =
        Files.readAllLines(Path.of("src/test/resources/benchmark/swift-corpus-labels.txt"));

    // When the Swift corpus smoke test runs (it needs the corpora, so it runs with
    // -Dward.corpus.swift; this test reads the labels it compares with the findings)
    final List<String> falseOfDecidedRules =
        labels.stream()
            .filter(
                line -> DECIDED.stream().anyMatch(
                    rule -> line.startsWith("ward:swift/" + rule + " |")
                )
            )
            .filter(line -> line.contains(" | fp | "))
            .toList();

    // Then no finding of a decided rule is labelled false
    assertThat(falseOfDecidedRules).isEmpty();
  }
}
