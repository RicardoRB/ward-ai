package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;

/** Runs one bundled Java injection rule through the rule engine on a single source file. */
final class JavaSecurityRuleRun {
  /** The imports of the servlet and JDBC types that the scenarios use. */
  static final String IMPORTS =
      """
      import java.io.File;
      import java.sql.Connection;
      import java.sql.Statement;
      import javax.servlet.http.HttpServletRequest;
      """;

  private JavaSecurityRuleRun() {}

  /** Returns the findings of the rule on the source, after checking that the engine ran. */
  static List<AnalysisFinding> findings(final String ruleId, final String source) {
    return findings(ruleId, source, Map.of());
  }

  /** Returns the findings of the rule, configured with the option values, on the source. */
  static List<AnalysisFinding> findings(
      final String ruleId,
      final String source,
      final Map<String,
      Object> options
  ) {
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            new AntlrLanguageFrontend(),
            DataFlowFixtures.FLOW,
            new JavaControlFlow()
        );
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream()
            .filter(rule -> rule.id().equals(ruleId))
            .map(rule -> RuleOptions.apply(rule, options))
            .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(new SourceUnit("Search.java", AnalysisLanguage.JAVA, source)),
                List.of(support),
                rules
            );
    assertThat(result.executionErrors()).isEmpty();
    return result.findings();
  }
}
