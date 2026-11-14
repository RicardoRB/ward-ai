package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.FlowStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The bundled Python taint rules, on the scenarios of their specification. */
class PythonSecurityRulesTest {

  private static final Path FIXTURES = Path.of("src/test/resources/rules/python");

  /** Scenario: a request value in a variable reaches execute, and the path lists the steps. */
  @Test
  void requestValueInAVariableReachesExecute() {
    final List<AnalysisFinding> found =
        PythonSecurityRules.run(
            """
            from flask import request

            def view(cursor):
                name = request.args.get("name")
                query = "SELECT * FROM t WHERE n = '" + name + "'"
                cursor.execute(query)
            """);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).isEqualTo("ward:python/sql-injection");
    assertThat(found.get(0).line()).isEqualTo(6);
    assertThat(found.get(0).path().stream().map(FlowStep::message).toList())
        .first()
        .isEqualTo("source: args");
  }

  /** Scenario: a value converted first reaches nothing. */
  @Test
  void convertedValueReportsNothing() {
    assertThat(
            PythonSecurityRules.run(
                """
                from flask import request

                def view(cursor):
                    n = int(request.args.get("n"))
                    cursor.execute("SELECT * FROM t WHERE n = " + str(n))
                """))
        .isEmpty();
  }

  /** Scenario: an imported alias of subprocess is still the subprocess module. */
  @Test
  void aliasedSubprocessIsACommandSink() {
    final List<AnalysisFinding> found =
        PythonSecurityRules.run(
            """
            import subprocess as sp
            from flask import request

            def view():
                cmd = request.form["cmd"]
                sp.run(cmd, shell=True)
            """);
    assertThat(found)
        .extracting(AnalysisFinding::ruleId)
        .containsExactly("ward:python/command-injection");
  }

  /** A FastAPI handler parameter is a source through its route decorator. */
  @Test
  void routeParameterIsASource() {
    final List<AnalysisFinding> found =
        PythonSecurityRules.run(
            """
            import os

            @app.get("/run")
            def run(cmd: str):
                os.system(cmd)
            """);
    assertThat(found)
        .extracting(AnalysisFinding::ruleId)
        .containsExactly("ward:python/command-injection");
  }

  /** Every fixture of the eight rules reports only the lines of its expected file. */
  @Test
  void fixturesReportTheirOwnRuleOnTheExpectedLines() throws IOException {
    for (final String rule :
        List.of(
            "sql-injection",
            "command-injection",
            "code-injection",
            "untrusted-deserialization",
            "path-injection",
            "untrusted-request-url",
            "untrusted-redirect",
            "template-injection")) {
      final String violating = Files.readString(FIXTURES.resolve(rule).resolve("violating.py"));
      final List<String> expected =
          Files.readAllLines(FIXTURES.resolve(rule).resolve("expected.txt")).stream()
              .filter(line -> !line.isBlank())
              .toList();
      assertThat(
              PythonSecurityRules.run(violating).stream()
                  .map(finding -> finding.ruleId() + " " + finding.line() + ":" + finding.column())
                  .toList())
          .as(rule)
          .containsExactlyElementsOf(
              expected.stream().map(position -> "ward:python/" + rule + " " + position).toList());
      assertThat(
              PythonSecurityRules.run(
                  Files.readString(FIXTURES.resolve(rule).resolve("passing.py"))))
          .as(rule + " passing")
          .isEmpty();
    }
  }
}
