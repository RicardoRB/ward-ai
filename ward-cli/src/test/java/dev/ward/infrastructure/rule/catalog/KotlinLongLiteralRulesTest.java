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
 * Runs every shipped Kotlin rule over a file whose string literals are very long, as generated test
 * data is. A regular expression that recurses once per character of a literal overflows the stack
 * there (#564, #565), so a rule that reads a literal must loop over it possessively.
 */
class KotlinLongLiteralRulesTest {
  private static final int REPEATS = 100_000;
  private static final String SOURCE =
      """
      package sample

      const val DATA = %1$s

      class LongLiteral(private val builder: StringBuilder) {
          fun run(input: String, other: Any?): Boolean {
              builder.append(%1$s)
              builder.append(%1$s).append(input)
              if (input == %1$s || other == null) {
                  return input.matches(Regex(%1$s))
              }
              println(String.format(%1$s, input) + "$input ${input.length}")
              return send(%1$s) && send(%1$s.toByteArray()) && DATA.isEmpty()
          }

          private fun send(value: Any?): Boolean = value != null
      }
      """;

  @Test
  void everyKotlinRuleReadsAVeryLongStringLiteral() {
    final String literal = "\"" + "a\\n".repeat(REPEATS) + "\"";
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.KOTLIN))
            .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(
                    new SourceUnit(
                        "LongLiteral.kt",
                        AnalysisLanguage.KOTLIN,
                        SOURCE.formatted(literal)
                    )
                ),
                new RuleFixtureRunner().frontends(),
                rules
            );
    assertThat(result.executionErrors()).isEmpty();
  }
}
