package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs the bundled TypeScript and JavaScript security rules over sources, as the gate does. */
final class NodeSecurityRules {

  /** The security rules of both languages: {@code ward:typescript/xss} and its JavaScript twin. */
  private static final String SECURITY_IDS =
      "ward:(typescript|javascript)/(command-injection|path-traversal|xss)";

  private NodeSecurityRules() {}

  /**
   * Runs the three bundled Node security rules over one source file.
   *
   * @param unit the source file, with its language
   * @return the findings, which the rule engine reports in order
   */
  static List<AnalysisFinding> run(final SourceUnit unit) {
    final List<RuleDefinition> rules =
        new ClasspathRuleSource().load().rules().stream()
            .filter(rule -> rule.id().matches(SECURITY_IDS))
            .filter(rule -> rule.languages().contains(unit.language()))
            .toList();
    final FrontendSupport support =
        new FrontendSupport(
            unit.language(),
            new AntlrLanguageFrontend(),
            DataFlowFixtures.FLOW,
            new NodeControlFlow());
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create()).run(List.of(unit), List.of(support), rules);
    return result.findings();
  }
}
