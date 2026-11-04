package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalysisGateTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.SPRING_BOOT);
  private static final String LENGTH = "ward:java/method-length";
  private static final String FLOW = "ward:java/data-flow";
  private static final Span AT = new Span(3, 5, 3, 9, 20, 24);

  @Test
  void passesWhenNoRuleReports() {
    final GateResult result =
        gate(
                List.of(rule(LENGTH, RuleCapability.SYNTAX, AnalysisSeverity.ERROR)),
                Map.of(LENGTH, List.of()),
                Set.of(RuleCapability.SYNTAX))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void failsOnErrorFindingWithRuleIdAndLocation() {
    final GateResult result =
        gate(
                List.of(rule(LENGTH, RuleCapability.SYNTAX, AnalysisSeverity.ERROR)),
                Map.of(LENGTH, List.of(new RuleMatch(AT, "too long"))),
                Set.of(RuleCapability.SYNTAX))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .containsExactly(
            new Finding(
                "ward",
                "ward",
                LENGTH,
                FindingSeverity.ERROR,
                "too long",
                "src/A.java",
                AT.startLine(),
                AT.startColumn(),
                null));
  }

  @Test
  void warningFindingDoesNotFailTheGate() {
    final GateResult result =
        gate(
                List.of(rule(LENGTH, RuleCapability.SYNTAX, AnalysisSeverity.WARNING)),
                Map.of(LENGTH, List.of(new RuleMatch(AT, "long"))),
                Set.of(RuleCapability.SYNTAX))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.findings().items()).hasSize(1);
  }

  @Test
  void rulesThatCannotRunAreUnavailableNotPassed() {
    final GateResult result =
        gate(
                List.of(rule(FLOW, RuleCapability.DATAFLOW, AnalysisSeverity.ERROR)),
                Map.of(FLOW, List.of(new RuleMatch(AT, "leak"))),
                Set.of(RuleCapability.SYNTAX))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.findings().items()).isEmpty();
    assertThat(result.stderr()).contains(FLOW).contains("DATAFLOW");
  }

  @Test
  void parseFailureIsExecutionErrorNotFinding() {
    final GateResult result =
        gate(
                List.of(rule(LENGTH, RuleCapability.SYNTAX, AnalysisSeverity.ERROR)),
                Map.of(LENGTH, List.of(new RuleMatch(AT, "long"))),
                Set.of(RuleCapability.SYNTAX),
                "src/Broken.java")
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.findings().items())
        .extracting(Finding::file)
        .doesNotContain("src/Broken.java");
    assertThat(result.stderr()).contains("src/Broken.java");
  }

  @Test
  void repeatedRunsGiveIdenticalFindings() {
    final AnalysisGate gate =
        gate(
            List.of(rule(LENGTH, RuleCapability.SYNTAX, AnalysisSeverity.ERROR)),
            Map.of(LENGTH, List.of(new RuleMatch(AT, "long"))),
            Set.of(RuleCapability.SYNTAX));

    assertThat(gate.execute(PROJECT).findings()).isEqualTo(gate.execute(PROJECT).findings());
  }

  private static AnalysisGate gate(
      final List<RuleDefinition> rules,
      final Map<String, List<RuleMatch>> matches,
      final Set<RuleCapability> capabilities) {
    return gate(rules, matches, capabilities, null);
  }

  private static AnalysisGate gate(
      final List<RuleDefinition> rules,
      final Map<String, List<RuleMatch>> matches,
      final Set<RuleCapability> capabilities,
      final String brokenPath) {
    final LanguageFrontend frontend =
        unit -> {
          if (unit.path().equals(brokenPath)) {
            throw new AnalysisFailedException("syntax error");
          }
          return new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
        };
    final RuleMatcher matcher =
        definition ->
            new CompiledRule() {
              @Override
              public RuleDefinition definition() {
                return definition;
              }

              @Override
              public List<RuleMatch> matches(final SyntaxNode root) {
                return matches.get(definition.id());
              }
            };
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(rules, List.of()),
            matcher,
            (root, languages) -> units(brokenPath, languages),
            List.of(new FrontendSupport(AnalysisLanguage.JAVA, frontend, capabilities)),
            root -> AnalysisCacheFixtures.NO_CACHE,
            AnalysisCacheFixtures.codec(),
            "test",
            root -> List.of());
    return new AnalysisGate("ward", rules, ports);
  }

  private static List<SourceUnit> units(
      final String brokenPath, final Set<AnalysisLanguage> languages) {
    final List<SourceUnit> all = new ArrayList<>();
    all.add(new SourceUnit("src/A.java", AnalysisLanguage.JAVA, ""));
    if (brokenPath != null) {
      all.add(new SourceUnit(brokenPath, AnalysisLanguage.JAVA, ""));
    }
    return all.stream().filter(unit -> languages.contains(unit.language())).toList();
  }

  private static RuleDefinition rule(
      final String id, final RuleCapability capability, final AnalysisSeverity severity) {
    return new RuleDefinition(id, AnalysisLanguage.JAVA, capability, severity, "body");
  }
}
