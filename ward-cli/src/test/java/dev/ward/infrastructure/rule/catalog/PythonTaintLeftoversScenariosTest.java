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

/** The Python scenarios of story #607: SQL sinks by scope facts and argument injection. */
class PythonTaintLeftoversScenariosTest {
  private static final String PREFIX = "ward:python/";

  private static List<String> reported(final String ruleName, final String source) {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.id().equals(PREFIX + ruleName))
            .toList();
    assertThat(rules).hasSize(1);
    final List<SourceUnit> units = List.of(
        new SourceUnit("app.py", AnalysisLanguage.PYTHON, source)
    );
    final List<AnalysisFinding> findings =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules)
            .findings();
    return findings.stream().map(finding -> finding.line() + ":" + finding.column()).toList();
  }

  @Test
  @DisplayName("a cursor under another name")
  void cursorUnderAnotherName() {
    // Given c = connection.cursor() and c.execute(query) with a tainted query
    final String unsafe =
        """
        import sqlite3
        from flask import request

        def find():
            connection = sqlite3.connect("app.db")
            c = connection.cursor()
            query = "SELECT * FROM users WHERE name = '" + request.args["name"] + "'"
            c.execute(query)
        """;
    final String reassigned =
        unsafe.replace("c = connection.cursor()", "c = Runner()");
    final String bound =
        unsafe.replace(
            "c.execute(query)",
            "c.execute(\"SELECT * FROM users WHERE name = ?\", (request.args[\"name\"],))"
        );

    // When the Python taint rules run
    final List<String> found = reported("sql-injection", unsafe);
    final List<String> other = reported("sql-injection", reassigned);
    final List<String> clean = reported("sql-injection", bound);

    // Then the call is reported, a receiver not assigned from a cursor is not, and bound parameters are not
    assertThat(found).containsExactly("8:5");
    assertThat(other).isEmpty();
    assertThat(clean).isEmpty();
  }

  @Test
  @DisplayName("argument injection")
  void argumentInjection() {
    // Given a tainted value used as the program name of an argument vector
    final String unsafe =
        """
        import subprocess
        from flask import request

        def run():
            program = request.args["tool"]
            subprocess.run([program, "--version"])
        """;
    final String sanitised = unsafe.replace(
        "request.args[\"tool\"]",
        "int(request.args[\"tool\"])"
    );

    // When the Python taint rules run
    final List<String> found = reported("argument-injection", unsafe);
    final List<String> clean = reported("argument-injection", sanitised);

    // Then it is reported as CWE-88, and a sanitised variant is not
    assertThat(found).containsExactly("6:5");
    assertThat(clean).isEmpty();
  }
}
