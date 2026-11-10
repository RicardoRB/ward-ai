package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/** Edge cases of the Pylint checks of assignments, comparisons, call arguments and globals. */
final class PylintFlowChecksTest {

  private static final String MESSAGE = "message";

  private static SyntaxNode root(final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source))
        .tree();
  }

  private static int count(final RuleCheck check, final String source) {
    return check.matches(root(source)).size();
  }

  private static AssignedCallCheck noReturn() {
    return new AssignedCallCheck(AssignedCallCheck.Problem.NO_RETURN, MESSAGE);
  }

  private static AssignedCallCheck none() {
    return new AssignedCallCheck(AssignedCallCheck.Problem.NONE, MESSAGE);
  }

  @Test
  void assignedCallWithoutReturnIsReportedOnlyForPlainFunctions() {
    assertThat(count(noReturn(), "def f():\n    print(1)\nx = f()\n")).isEqualTo(1);
    assertThat(count(noReturn(), "def f():\n    raise ValueError()\nx = f()\n")).isZero();
    assertThat(count(noReturn(), "def f():\n    yield 1\nx = f()\n")).isZero();
    assertThat(count(noReturn(), "def f():\n    print(1)\nx += f()\n")).isZero();
    assertThat(count(noReturn(), "def f():\n    print(1)\nx = f()[0]\n")).isZero();
    assertThat(count(noReturn(), "@deco\ndef f():\n    print(1)\nx = f()\n")).isZero();
  }

  @Test
  void assignedCallOfNoneReturnIsReported() {
    assertThat(count(none(), "def f(x):\n    if x:\n        return None\n    return\nx = f(1)\n"))
        .isEqualTo(1);
    assertThat(count(none(), "def f(x):\n    if x:\n        return 1\n    return None\nx = f(1)\n"))
        .isZero();
    assertThat(count(none(), "def f():\n    pass\nx = f()\n")).isZero();
  }

  @Test
  void bareCallableComparedWithAValueIsReported() {
    final String function = "def h():\n    return 1\n";
    assertThat(count(new CallableComparisonCheck(MESSAGE), function + "if h == 1:\n    pass\n"))
        .isEqualTo(1);
    assertThat(count(new CallableComparisonCheck(MESSAGE), function + "if h == h:\n    pass\n"))
        .isZero();
    assertThat(count(new CallableComparisonCheck(MESSAGE), function + "if h() == 1:\n    pass\n"))
        .isZero();
  }

  @Test
  void argumentsNamingTheParametersInAnotherOrderAreReported() {
    final ArgumentOrderCheck check = new ArgumentOrderCheck(MESSAGE);
    final String function = "def g(a, b):\n    return a\n";
    assertThat(count(check, function + "a = 1\nb = 2\ng(b, a)\n")).isEqualTo(1);
    assertThat(count(check, function + "a = 1\nb = 2\ng(a, b)\n")).isZero();
    assertThat(count(check, function + "a = 1\ng(b=a, a=1)\n")).isZero();
    assertThat(count(check, function + "a = 1\nh(b, a)\n")).isZero();
  }

  @Test
  void globalAssignedButNeverDefinedAtModuleLevelIsReported() {
    final GlobalUndefinedCheck check = new GlobalUndefinedCheck(MESSAGE);
    assertThat(count(check, "def f():\n    global c\n    c = 1\n")).isEqualTo(1);
    assertThat(count(check, "c = 0\ndef f():\n    global c\n    c = 1\n")).isZero();
    assertThat(count(check, "from m import c\ndef f():\n    global c\n    c = 1\n")).isZero();
    assertThat(count(check, "def f():\n    global c\n    print(c)\n")).isZero();
  }
}
