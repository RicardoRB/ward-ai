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
 * Runs every shipped Swift rule over a file whose string literals are very long, as generated test
 * data is. A regular expression that recurses once per character of a literal overflows the stack
 * there (#564), so a rule that reads a literal must loop over it possessively.
 */
class SwiftLongLiteralRulesTest {
  private static final int REPEATS = 100_000;
  private static final String SOURCE =
      """
      import Foundation

      final class LongLiteral {
          private let data = %1$s
          private var parts: [String] = []

          func run(_ input: String, other: Any?) -> Bool {
              parts.append(%1$s)
              parts.append(%1$s + input)
              if input == %1$s || other == nil {
                  return input.contains(%1$s)
              }
              print(String(format: %1$s, input))
              let url = URL(string: %1$s)
              return send(%1$s) && send(%1$s.utf8) && data.isEmpty && url != nil
          }

          private func send(_ value: Any) -> Bool {
              return "\\(value)" != %1$s
          }
      }
      """;

  @Test
  void everySwiftRuleReadsAVeryLongStringLiteral() {
    final String literal = "\"" + "a\\n".repeat(REPEATS) + "\"";
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.SWIFT))
            .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(
                    new SourceUnit(
                        "LongLiteral.swift",
                        AnalysisLanguage.SWIFT,
                        SOURCE.formatted(literal)
                    )
                ),
                new RuleFixtureRunner().frontends(),
                rules
            );
    assertThat(result.executionErrors()).isEmpty();
  }
}
