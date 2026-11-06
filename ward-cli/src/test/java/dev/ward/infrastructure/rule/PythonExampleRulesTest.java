package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the Python example rule of each generic check kind over real sources. */
class PythonExampleRulesTest {

  private static final String PYTHON = "ward:python/example-";
  private static final String PYTHON_SOURCE =
      """
      import os
      import os
      from m import *
      def Bad(a, c, d):
          x = 60
          if a:
              if c:
                  if d:
                      return 1
          return 2
          return 3
          y = 1\s
      def good():
          try:
              pass
          finally:
              pass
      """;
  private static final String PYTHON_CLEAN =
      """
      import os

      def good(a):
          return os.sep
      """;

  @Test
  void pythonExamplesReportTheirViolations() {
    final List<AnalysisFinding> findings =
        ExampleRuns.run(AnalysisLanguage.PYTHON, "python", PYTHON_SOURCE);

    assertThat(ExampleRuns.lines(findings, PYTHON + "function-name"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "def Bad"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "wildcard-import"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "from m"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "try-needs-except"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "try:"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "return-count"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "def Bad"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "nested-if"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "if d:"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "trailing-whitespace"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "y = 1"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "block-order"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "return 2", "return 3", "y = 1"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "magic-number"))
        .containsExactlyElementsOf(ExampleRuns.at(PYTHON_SOURCE, "x = 60", "return 2", "return 3"));
    assertThat(ExampleRuns.lines(findings, PYTHON + "duplicate-import")).containsExactly(2);
  }

  @Test
  void aCompliantPythonFileHasNoExampleFinding() {
    assertThat(ExampleRuns.run(AnalysisLanguage.PYTHON, "python", PYTHON_CLEAN)).isEmpty();
  }
}
