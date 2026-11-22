package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the Rust frontend and every shipped Rust rule over a file whose string literals are very
 * long, as generated test data is. A regular expression that recurses once per character of a
 * literal overflows the stack there (#564), so a rule that reads a literal must loop over it
 * possessively; the parser, the scopes and the control-flow lowering must read it too.
 */
class RustLongLiteralRulesTest {
  private static final int REPEATS = 100_000;
  private static final String SOURCE =
      """
      use std::fmt::Write;

      struct LongLiteral {
          data: &'static str,
      }

      impl LongLiteral {
          fn run(&self, input: &str, other: Option<u8>) -> bool {
              let mut parts: Vec<String> = Vec::new();
              parts.push(%1$s.to_string());
              parts.push(format!("{}{}", %1$s, input));
              if input == %1$s || other.is_none() {
                  return input.contains(%1$s);
              }
              let raw = r#%1$s#;
              let bytes = b%1$s;
              println!(%1$s, input);
              let url = send(%1$s) && send(%1$s) && self.data.is_empty();
              url && raw.len() > bytes.len()
          }
      }

      fn send(value: &str) -> bool {
          value != %1$s
      }
      """;

  @Test
  @DisplayName("a very long literal")
  void everyRustRuleReadsAVeryLongStringLiteral() {
    final String literal = "\"" + "a\\n".repeat(REPEATS) + "\"";
    final String text = SOURCE.formatted(literal);
    final SourceUnit unit = new SourceUnit("LongLiteral.rs", AnalysisLanguage.RUST, text);
    final List<FrontendSupport> frontends = new RuleFixtureRunner().frontends();
    final FrontendSupport rust =
        frontends.stream()
            .filter(support -> support.language() == AnalysisLanguage.RUST)
            .findFirst()
            .orElseThrow();
    final ParseResult parsed = rust.frontend().parse(unit);

    assertThat(parsed.diagnostics()).isEmpty();
    assertThat(rust.scopes().analyze(unit, parsed.tree()).references()).isNotEmpty();
    assertThat(rust.lowering().lower(unit, parsed.tree()).graphs()).hasSize(2);

    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.RUST))
            .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create()).run(List.of(unit), frontends, rules);
    assertThat(result.executionErrors()).isEmpty();
  }
}
