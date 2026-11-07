package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** A rule that needs types is never passed without a native type resolver for its language. */
class TypeRuleGateTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.SPRING_BOOT);
  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);
  private static final RuleDefinition TYPED =
      new RuleDefinition(
          "ward:java/data-flow",
          Set.of(AnalysisLanguage.JAVA),
          RuleCapability.TYPES,
          AnalysisSeverity.ERROR,
          "body");

  @Test
  void typeRuleWithoutNativeResolverIsNotRunAndTheGateIsUnavailable() {
    final GateResult result = gate((root, classpath) -> Map.of()).execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.stderr())
        .contains("ward:java/data-flow not run")
        .contains("no native type resolver is registered for JAVA yet");
  }

  @Test
  void typeRuleRunsWhenANativeResolverIsRegisteredForItsLanguage() {
    final TypeFacts resolver = (unit, spans, kinds) -> UnitTypeFacts.none();
    final GateResult result =
        gate((root, classpath) -> Map.of(AnalysisLanguage.JAVA, resolver)).execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.stderr()).doesNotContain("not run");
  }

  private static AnalysisGate gate(
      final BiFunction<Path, Optional<Path>, Map<AnalysisLanguage, TypeFacts>> nativeTypeFacts) {
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(List.of(TYPED), List.of()),
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
            nativeTypeFacts);
    return new AnalysisGate("ward", List.of(TYPED), ports);
  }
}
