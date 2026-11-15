package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs every shipped Java rule over a file whose string literals are very long, as generated test
 * data is. A regular expression that recurses once per character of a literal overflows the stack
 * there (#564), so a rule that reads a literal must loop over it possessively.
 */
class JavaLongLiteralRulesTest {
  private static final int REPEATS = 100_000;
  private static final String SOURCE =
      """
      class LongLiteral {
        private static final String DATA = %1$s;
        private final StringBuilder builder = new StringBuilder();

        boolean run(final String input, final Object other) {
          builder.append(%1$s);
          builder.append(%1$s).append(input);
          if (input.equals(%1$s) || other == null) {
            return input.matches(%1$s);
          }
          System.out.println(String.format(%1$s, input));
          return send(%1$s) && send(%1$s.getBytes()) && DATA.isEmpty();
        }

        private boolean send(final Object value) {
          return value != null;
        }
      }
      """;

  @Test
  void everyJavaRuleReadsAVeryLongStringLiteral() {
    final String literal = "\"" + "a\\n".repeat(REPEATS) + "\"";
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.JAVA))
            .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(
                    new SourceUnit(
                        "LongLiteral.java",
                        AnalysisLanguage.JAVA,
                        SOURCE.formatted(literal)
                    )
                ),
                new RuleFixtureRunner().frontends(),
                rules
            );
    assertThat(result.executionErrors()).isEmpty();
  }
}
