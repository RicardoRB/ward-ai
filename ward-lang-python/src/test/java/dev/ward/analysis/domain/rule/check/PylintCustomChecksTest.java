package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/** Edge cases of the Pylint-derived custom checks, parsed from inline source. */
final class PylintCustomChecksTest {

  private static final String MESSAGE = "message";

  private static SyntaxNode root(final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source))
        .tree();
  }

  private static int count(final RuleCheck check, final String source) {
    return check.matches(root(source)).size();
  }

  @Test
  void elifAfterReturningBranchIsReported() {
    assertThat(
            count(
                new ElseAfterJumpCheck("return", MESSAGE),
                "def f(x):\n    if x:\n        return 1\n    elif x > 2:\n        return 2\n    return 3\n"))
        .isEqualTo(1);
  }

  @Test
  void stringAfterAssignmentInAFunctionIsReported() {
    assertThat(
            count(
                new PointlessStringCheck(),
                "def f():\n    x = 1\n    \"after the assignment\"\n    return x\n"))
        .isEqualTo(1);
  }

  @Test
  void stringAfterAssignmentInInitIsAttributeDocstring() {
    assertThat(
            count(
                new PointlessStringCheck(),
                "class A:\n    def __init__(self):\n        self.x = 1\n        \"attribute\"\n"))
        .isZero();
  }

  @Test
  void unsupportedFormatCharacterNeedsArguments() {
    assertThat(
            count(
                new LoggingFormatCheck(LoggingFormatCheck.Problem.UNSUPPORTED, MESSAGE),
                "import logging\nlogging.info(\"%y\")\n"))
        .isZero();
    assertThat(
            count(
                new LoggingFormatCheck(LoggingFormatCheck.Problem.UNSUPPORTED, MESSAGE),
                "import logging\nlogging.info(\"%y\", 1)\n"))
        .isEqualTo(1);
  }

  @Test
  void truncatedFormatIsReported() {
    assertThat(
            count(
                new LoggingFormatCheck(LoggingFormatCheck.Problem.TRUNCATED, MESSAGE),
                "import logging\nlogging.info(\"abc %\")\n"))
        .isEqualTo(1);
  }

  @Test
  void literalFormatIndexOutOfRangeIsReportedButNamesAreNot() {
    assertThat(count(new FormatIndexCheck(MESSAGE), "x = \"{0[5]}\".format(xs)\n")).isZero();
    assertThat(count(new FormatIndexCheck(MESSAGE), "x = \"{0[5]}\".format([1, 2])\n"))
        .isEqualTo(1);
  }

  @Test
  void dictModifiedWhileIteratedIsReportedOnlyForLiteralDict() {
    assertThat(
            count(
                new ModifiedIterationCheck(ModifiedIterationCheck.Kind.DICT, MESSAGE),
                "d = {\"a\": 1}\nfor k in d:\n    del d[k]\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new ModifiedIterationCheck(ModifiedIterationCheck.Kind.DICT, MESSAGE),
                "d = load()\nfor k in d:\n    del d[k]\n"))
        .isZero();
  }
}
