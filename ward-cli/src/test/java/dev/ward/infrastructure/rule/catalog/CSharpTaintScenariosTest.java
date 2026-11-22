package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story #665: security rules and taint analysis for C#. */
class CSharpTaintScenariosTest {
  private static final int SINK_LINE = 7;

  private static final String UNSAFE =
      """
      using Microsoft.AspNetCore.Mvc;
      using System.Data.SqlClient;

      public class UserController : ControllerBase {
        public IActionResult Find() {
          var name = Request.Query["name"];
          return Ok(Lookup(name));
        }

        private string Lookup(string name) {
          return Run("SELECT * FROM users WHERE name = '" + name + "'");
        }

        private string Run(string text) {
          var command = new SqlCommand();
          command.CommandText = text;
          return command.ExecuteScalar().ToString();
        }
      }
      """;

  private static List<AnalysisFinding> reported(final String source) {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> "ward:csharp/sql-injection".equals(rule.id()))
            .toList();
    assertThat(rules).hasSize(1);
    final List<SourceUnit> units =
        List.of(new SourceUnit("src/UserController.cs", AnalysisLanguage.CSHARP, source));
    return new RuleEngine(BundledRuleMatchers.create())
        .run(units, new RuleFixtureRunner().frontends(), rules)
        .findings();
  }

  @Test
  @DisplayName("an injection across functions")
  void anInjectionAcrossFunctions() {
    // Given a query string value that reaches SqlCommand.CommandText through two methods
    // When the ward gate runs
    final List<AnalysisFinding> found = reported(UNSAFE);

    // Then it reports the flow into the sink, with the source in the finding
    assertThat(found).hasSize(1);
    assertThat(found.get(0).line()).isEqualTo(SINK_LINE);
    assertThat(found.get(0).message()).contains("Query");
  }

  @Test
  @DisplayName("a sanitized value")
  void aSanitizedValue() {
    // Given the same flow through a sanitizer
    final String sanitized =
        UNSAFE.replace(
            "var name = Request.Query[\"name\"];",
            "var name = int.Parse(Request.Query[\"name\"]);"
        );

    // When the ward gate runs
    final List<AnalysisFinding> found = reported(sanitized);

    // Then it reports nothing
    assertThat(found).isEmpty();
  }
}
