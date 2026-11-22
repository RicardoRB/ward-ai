package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs every shipped C# rule over a file whose string literals are very long, as generated test
 * data is. A regular expression that recurses once per character of a literal overflows the stack
 * there (#564), so a rule that reads a literal must loop over it possessively.
 */
class CSharpLongLiteralRulesTest {
  private static final int REPEATS = 100_000;
  private static final String SOURCE =
      """
      using System;

      namespace Demo;

      public sealed class LongLiteral
      {
          private readonly string data = %1$s;

          public bool Run(string input, object other)
          {
              var parts = new System.Collections.Generic.List<string> { %1$s, %1$s + input };
              if (input == %1$s || other is null)
              {
                  return input.Contains(%1$s);
              }
              Console.WriteLine(string.Format(%1$s, input));
              var raw = %2$s;
              return Send(%1$s) && Send(%1$s.Length) && data.Length > 0 && raw.Length > 0;
          }

          private static bool Send(object value) => $"{value}" != %1$s;
      }
      """;

  @DisplayName("a very long literal")
  @Test
  void everyCSharpRuleReadsAVeryLongStringLiteral() {
    final String literal = "\"" + "a\\n".repeat(REPEATS) + "\"";
    final String raw = "\"\"\"\n" + "line of a raw literal\n".repeat(REPEATS) + "\"\"\"";
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.CSHARP))
            .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(
                    new SourceUnit(
                        "LongLiteral.cs",
                        AnalysisLanguage.CSHARP,
                        SOURCE.formatted(literal, raw)
                    )
                ),
                new RuleFixtureRunner().frontends(),
                rules
            );
    assertThat(rules).isNotEmpty();
    assertThat(result.executionErrors()).isEmpty();
    assertThat(result.findings()).isEmpty();
  }
}
