package dev.ward.analysis.infrastructure.node;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Runs one structural rule over one source through the real frontend and engine. */
final class NodeRuleRuns {

  private NodeRuleRuns() {}

  /**
   * Returns the id of a structural rule for {@code language}, such as {@code
   * ward:typescript/naming}.
   */
  static String id(final AnalysisLanguage language, final String metric) {
    return "ward:" + language.name().toLowerCase(Locale.ROOT) + "/" + metric;
  }

  /** Runs one rule with {@code body} over {@code text}, as the {@code ward} gate would. */
  static RuleRunResult run(
      final String metric, final AnalysisLanguage language, final String body, final String text) {
    final RuleDefinition rule =
        new RuleDefinition(
            id(language, metric), language, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, body);
    final SourceUnit unit = new SourceUnit("src/" + metric + ".txt", language, text);
    final FrontendSupport support =
        new FrontendSupport(language, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    return new RuleEngine(new NodeRuleMatcher())
        .run(List.of(unit), List.of(support), List.of(rule));
  }

  /** Returns what one rule measures in {@code text}, parsed by the real frontend. */
  static List<Measured> measure(
      final String metric, final AnalysisLanguage language, final String text) {
    final RuleDefinition rule =
        new RuleDefinition(
            id(language, metric), language, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, "");
    final SourceUnit unit = new SourceUnit("src/" + metric + ".txt", language, text);
    return new NodeRuleMatcher()
        .compile(rule)
        .measures(new AntlrLanguageFrontend().parse(unit).tree());
  }
}
