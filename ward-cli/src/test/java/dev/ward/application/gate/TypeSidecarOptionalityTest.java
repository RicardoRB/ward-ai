package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.TypeResolver;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The sidecar is optional: the same units and rules give the same syntax findings, and the same
 * skipped rules apart from the TYPES rules, with and without a sidecar that starts.
 */
class TypeSidecarOptionalityTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.SPRING_BOOT);
  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);
  private static final String SYNTAX = "ward:java/method-length";
  private static final String TYPED = "ward:java/data-flow";

  @Test
  void syntaxFindingsAndSkippedRulesMatchWithAndWithoutAStartedSidecar() {
    final CountingSidecar sidecar = new CountingSidecar();
    final GateResult without = run(Map.of());
    final GateResult with = run(Map.of(AnalysisLanguage.JAVA, sidecar));

    assertThat(with.findings().items()).isEqualTo(without.findings().items());
    assertThat(with.status()).isEqualTo(without.status());
    assertThat(lines(with.stderr())).containsExactlyElementsOf(lines(without.stderr()));
    assertThat(sidecar.starts).isOne();
  }

  @Test
  void aSidecarThatCannotStartChangesNothingButTheTypedRule() {
    final GateResult without = run(Map.of());
    final GateResult failed = run(Map.of(AnalysisLanguage.JAVA, new CountingSidecar(false)));

    assertThat(failed.findings().items()).isEqualTo(without.findings().items());
    assertThat(failed.status()).isEqualTo(without.status());
    assertThat(lines(failed.stderr())).containsExactlyElementsOf(lines(without.stderr()));
  }

  /** Every output line except the lines of the TYPES rule, which runs only with a sidecar. */
  private static List<String> lines(final String stderr) {
    return stderr.lines().filter(line -> !line.contains(TYPED)).toList();
  }

  private static GateResult run(final Map<AnalysisLanguage, TypeSidecar> sidecars) {
    final List<RuleDefinition> rules =
        List.of(
            new RuleDefinition(
                SYNTAX,
                Set.of(AnalysisLanguage.JAVA),
                RuleCapability.SYNTAX,
                AnalysisSeverity.ERROR,
                "body"),
            new RuleDefinition(
                TYPED,
                Set.of(AnalysisLanguage.JAVA),
                RuleCapability.TYPES,
                AnalysisSeverity.ERROR,
                "body"));
    final RuleMatcher matcher =
        definition ->
            new CompiledRule() {
              @Override
              public RuleDefinition definition() {
                return definition;
              }

              @Override
              public List<RuleMatch> matches(final SyntaxNode root) {
                return SYNTAX.equals(definition.id())
                    ? List.of(new RuleMatch(AT, "too long"))
                    : List.of();
              }
            };
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(rules, List.of()),
            matcher,
            (root, languages) ->
                List.of(new SourceUnit("src/A.java", AnalysisLanguage.JAVA, "class A {}")),
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
            root -> sidecars);
    return new AnalysisGate("ward", rules, ports).execute(PROJECT);
  }

  /** A sidecar that counts its starts and answers no span. */
  private static final class CountingSidecar implements TypeSidecar {
    private final boolean startable;
    int starts;

    CountingSidecar() {
      this(true);
    }

    CountingSidecar(final boolean startable) {
      this.startable = startable;
    }

    @Override
    public Optional<TypeResolver> start() {
      starts++;
      return startable
          ? Optional.of((SourceUnit unit, List<Span> spans) -> ExpressionTypes.none())
          : Optional.empty();
    }

    @Override
    public void close() {
      // Nothing runs, so there is nothing to stop.
    }
  }
}
