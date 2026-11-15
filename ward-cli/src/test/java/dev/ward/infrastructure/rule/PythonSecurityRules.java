package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.python.PythonControlFlow;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs the bundled Python taint rules over a source, as the gate does. */
final class PythonSecurityRules {

  private static final String TAINT_IDS =
      "ward:python/(sql-injection|command-injection|code-injection|untrusted-deserialization"
          + "|path-injection|untrusted-request-url|untrusted-redirect|template-injection)";

  private PythonSecurityRules() {}

  /** Runs the eight taint rules over one source file and returns the findings in order. */
  static List<AnalysisFinding> run(final String text) {
    final SourceUnit unit = new SourceUnit("app.py", AnalysisLanguage.PYTHON, text);
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(rule -> rule.id().matches(TAINT_IDS)).toList();
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.PYTHON,
            new AntlrLanguageFrontend(),
            DataFlowFixtures.FLOW,
            new PythonControlFlow());
    return new RuleEngine(BundledRuleMatchers.create())
        .run(List.of(unit), List.of(support), rules)
        .findings();
  }
}
