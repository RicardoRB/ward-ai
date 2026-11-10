package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

/** Grammar predicates and actions for PythonPredicate, exercised through the frontend. */
class PythonPredicateTest {

  private static ParseResult parse(final AnalysisLanguage language, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit("sample", language, text));
  }

  @Test
  void pythonTabAndNestedDedentParse() {
    final String text = "if a:\n\tif b:\n\t\tpass\nx = 1\n";

    assertThat(parse(AnalysisLanguage.PYTHON, text).diagnostics()).isEmpty();
  }

  @Test
  void pythonBlockAtEndOfFileWithoutTrailingNewlineParses() {
    assertThat(parse(AnalysisLanguage.PYTHON, "def f():\n    return 1").diagnostics()).isEmpty();
  }

  @Test
  void pythonContinuationInsideParenthesesIgnoresLineBreaks() {
    assertThat(parse(AnalysisLanguage.PYTHON, "x = (1,\n     2)\n").diagnostics()).isEmpty();
  }

  @Test
  void pythonInconsistentDedentIsReported() {
    final String text = "def f(:\n    return 1\n";

    assertThat(parse(AnalysisLanguage.PYTHON, text).diagnostics()).isNotEmpty();
  }

  @Test
  void validPythonConstructsParseWithoutDiagnostics() {
    final String[] snippets = {
      "x = [\n    1,\n]\n",
      "x = 1\n\n\ny = 2\n",
      "# only a comment\nx = 1  # trailing\n",
      "x = 1\r\ny = 2\r\n",
      "def f(a,\n      b):\n    return a + b\n",
    };
    for (final String snippet : snippets) {
      assertThat(parse(AnalysisLanguage.PYTHON, snippet).diagnostics()).as(snippet).isEmpty();
    }
  }
}
