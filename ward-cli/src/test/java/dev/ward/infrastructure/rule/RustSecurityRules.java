package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rust.RustControlFlow;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs the bundled Rust taint rules over a source, as the gate does. */
final class RustSecurityRules {
  private static final String TAINT_IDS =
      "ward:rust/(sql-injection|command-injection|path-injection)";

  private RustSecurityRules() {}

  /** Runs the three taint rules over one source file and returns the findings in order. */
  static List<AnalysisFinding> run(final String text) {
    final SourceUnit unit = new SourceUnit("main.rs", AnalysisLanguage.RUST, text);
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(rule -> rule.id().matches(TAINT_IDS)).toList();
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.RUST,
            new AntlrLanguageFrontend(),
            DataFlowFixtures.FLOW,
            new RustControlFlow()
        );
    return new RuleEngine(BundledRuleMatchers.create())
        .run(List.of(unit), List.of(support), rules)
        .findings();
  }
}
