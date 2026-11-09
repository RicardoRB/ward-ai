package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks the Swift whole-run unused rules: declarations, assigned-only properties. */
final class SwiftUnusedDefinitionFindingsTest {

  private static final Span SPAN = new Span(3, 1, 4, 1, 10, 20);
  private static final String LIB_SWIFT = "Sources/Lib.swift";
  private static final String APP_SWIFT = "Sources/App.swift";
  private static final String LIB_PY = "a/lib.py";
  private static final RuleDefinition SWIFT_DECLARATION =
      rule("ward:swift/unused-declaration", AnalysisLanguage.SWIFT);
  private static final RuleDefinition SWIFT_ASSIGN_ONLY =
      rule("ward:swift/assign-only-property", AnalysisLanguage.SWIFT);

  @Test
  void aSwiftFunctionNoOtherFileUsesIsReported() {
    final Map<String, FileSummary> files =
        Map.of(
            LIB_SWIFT,
            summary(LIB_SWIFT, Set.of(), symbol("shared"), symbol("lonely")),
            APP_SWIFT,
            summary(APP_SWIFT, Set.of("shared")));

    assertThat(UnusedDefinitionFindings.findings(List.of(SWIFT_DECLARATION), files))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("lonely");
  }

  @Test
  void theSwiftRuleSkipsPythonFiles() {
    final Map<String, FileSummary> files =
        Map.of(LIB_PY, summary(LIB_PY, Set.of(), symbol("lonely")));

    assertThat(UnusedDefinitionFindings.findings(List.of(SWIFT_DECLARATION), files)).isEmpty();
  }

  @Test
  void aSwiftPropertyThatIsAssignedButNeverReadIsReported() {
    final FileSummary lib =
        new FileSummary(
            LIB_SWIFT,
            List.of(),
            List.of(),
            List.of(
                new Symbol("count", SPAN, "property"),
                new Symbol("label", SPAN, "property"),
                new Symbol("idle", SPAN, "property")),
            Set.of("label"),
            Set.of("count", "label"));

    assertThat(
            UnusedDefinitionFindings.findings(List.of(SWIFT_ASSIGN_ONLY), Map.of(LIB_SWIFT, lib)))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .isEqualTo("property 'count' is assigned but never read in any file");
  }

  private static Symbol symbol(final String name) {
    return new Symbol(name, SPAN, "function");
  }

  private static FileSummary summary(
      final String path, final Set<String> used, final Symbol... declared) {
    return new FileSummary(path, List.of(), List.of(), List.of(declared), used);
  }

  private static RuleDefinition rule(final String id, final AnalysisLanguage language) {
    return new RuleDefinition(
        id,
        Set.of(language),
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        "check = \"unused-definition\"");
  }

  @Test
  void aProtocolUsedOnlyToConformIsReported() {
    final RuleDefinition redundant = rule("ward:swift/redundant-protocol", AnalysisLanguage.SWIFT);
    final Map<String, FileSummary> files =
        Map.of(
            LIB_SWIFT,
            new FileSummary(
                LIB_SWIFT,
                List.of(),
                List.of(),
                List.of(new Symbol("Drawable", SPAN, "protocol")),
                Set.of()),
            APP_SWIFT,
            summary(APP_SWIFT, Set.of("conformance:Drawable")));

    assertThat(UnusedDefinitionFindings.findings(List.of(redundant), files))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("Drawable");
  }

  @Test
  void aProtocolAlsoUsedAsATypeIsNotReported() {
    final RuleDefinition redundant = rule("ward:swift/redundant-protocol", AnalysisLanguage.SWIFT);
    final Map<String, FileSummary> files =
        Map.of(
            LIB_SWIFT,
            new FileSummary(
                LIB_SWIFT,
                List.of(),
                List.of(),
                List.of(new Symbol("Drawable", SPAN, "protocol")),
                Set.of()),
            APP_SWIFT,
            summary(APP_SWIFT, Set.of("conformance:Drawable", "Drawable")));

    assertThat(UnusedDefinitionFindings.findings(List.of(redundant), files)).isEmpty();
  }
}
