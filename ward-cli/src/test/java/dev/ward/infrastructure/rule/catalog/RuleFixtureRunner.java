package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import java.util.Set;

/**
 * Runs one bundled rule over one source text through the real rule engine, with the rule's default
 * options and the frontends of the {@code ward} gate. One runner serves one test at a time, since
 * the frontend is not thread-safe.
 */
final class RuleFixtureRunner {

  private static final Set<RuleCapability> SYNTAX = Set.of(RuleCapability.SYNTAX);
  private static final Set<RuleCapability> SYMBOLS =
      Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS);
  private static final Set<RuleCapability> FLOW =
      Set.of(
          RuleCapability.SYNTAX,
          RuleCapability.SYMBOLS,
          RuleCapability.CFG,
          RuleCapability.DATAFLOW);

  private final List<FrontendSupport> frontends;

  /** Creates a runner whose frontends match the {@code ward} gate. */
  RuleFixtureRunner() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    frontends =
        List.of(
            new FrontendSupport(AnalysisLanguage.JAVA, frontend, FLOW, new JavaControlFlow()),
            new FrontendSupport(AnalysisLanguage.PYTHON, frontend, SYMBOLS),
            new FrontendSupport(AnalysisLanguage.TYPESCRIPT, frontend, FLOW, new NodeControlFlow()),
            new FrontendSupport(AnalysisLanguage.JAVASCRIPT, frontend, FLOW, new NodeControlFlow()),
            new FrontendSupport(AnalysisLanguage.SWIFT, frontend, SYMBOLS),
            new FrontendSupport(AnalysisLanguage.KOTLIN, frontend, SYNTAX));
  }

  /**
   * Returns the findings of {@code rule} on {@code text}, after checking that the engine ran the
   * rule without an error. {@code typeFacts} are the facts of the fixture's {@code types.json}; the
   * engine takes no type facts yet, so they reach a rule once the first type-aware rule lands (see
   * {@link FixtureTypeFacts}).
   */
  List<AnalysisFinding> run(
      final RuleDefinition rule, final String path, final String text, final TypeFacts typeFacts) {
    final AnalysisLanguage language = rule.languages().iterator().next();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(List.of(new SourceUnit(path, language, text)), frontends, List.of(rule));
    assertThat(result.executionErrors()).as("execution errors of %s", rule.id()).isEmpty();
    assertThat(result.notRun()).as("rules not run: %s", rule.id()).isEmpty();
    return result.findings().stream()
        .filter(finding -> finding.ruleId().equals(rule.id()))
        .toList();
  }
}
