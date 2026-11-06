package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.jvm.JavaTypeSidecar;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A rule that needs types is skipped, without failing the gate, when no sidecar runs. */
class TypeRuleGateTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.SPRING_BOOT);
  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void typeRuleWithoutSidecarIsSkippedWithoutFailingTheGate() {
    final RuleDefinition syntax =
        new RuleDefinition(
            "ward:java/method-length",
            Set.of(AnalysisLanguage.JAVA),
            RuleCapability.SYNTAX,
            AnalysisSeverity.ERROR,
            "body");
    final RuleDefinition typed =
        new RuleDefinition(
            "ward:java/data-flow",
            Set.of(AnalysisLanguage.JAVA),
            RuleCapability.TYPES,
            AnalysisSeverity.ERROR,
            "body");
    final GateResult result = gate(List.of(syntax, typed)).execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.stderr())
        .contains("ward:java/data-flow not run")
        .contains("type sidecar is unavailable");
  }

  @Test
  void javaTypeRuleWithoutAJavaClasspathIsNotRunBecauseTheSidecarIsUnavailable() {
    final RuleDefinition typed =
        new RuleDefinition(
            "ward:java/data-flow",
            Set.of(AnalysisLanguage.JAVA),
            RuleCapability.TYPES,
            AnalysisSeverity.ERROR,
            "body");
    final GateResult result =
        gate(List.of(typed), root -> Map.of(AnalysisLanguage.JAVA, new JavaTypeSidecar(root, "t")))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.stderr())
        .contains("ward:java/data-flow not run")
        .contains("java-classpath")
        .contains("type sidecar is unavailable");
  }

  private static AnalysisGate gate(final List<RuleDefinition> rules) {
    return gate(rules, unused -> Map.of());
  }

  private static AnalysisGate gate(
      final List<RuleDefinition> rules,
      final java.util.function.Function<Path, Map<AnalysisLanguage, TypeSidecar>> sidecars) {
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(rules, List.of()),
            definition ->
                new CompiledRule() {
                  @Override
                  public RuleDefinition definition() {
                    return definition;
                  }

                  @Override
                  public List<RuleMatch> matches(final SyntaxNode root) {
                    return List.of();
                  }
                },
            (root, languages) -> List.of(new SourceUnit("src/A.java", AnalysisLanguage.JAVA, "")),
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.JAVA,
                    unit ->
                        new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of()),
                    Set.of(RuleCapability.SYNTAX))),
            root -> AnalysisCacheFixtures.NO_CACHE,
            AnalysisCacheFixtures.codec(),
            "test",
            DependencyAdapters.none(),
            sidecars);
    return new AnalysisGate("ward", rules, ports);
  }
}
