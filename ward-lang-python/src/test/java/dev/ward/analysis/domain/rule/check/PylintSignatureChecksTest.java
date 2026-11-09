package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/** Edge cases of the Pylint override signature checks: arguments-differ and arguments-renamed. */
final class PylintSignatureChecksTest {

  private static final String MESSAGE = "message";

  private static SyntaxNode root(final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source))
        .tree();
  }

  private static int count(final RuleCheck check, final String source) {
    return check.matches(root(source)).size();
  }

  private static SignatureOverrideCheck differs() {
    return new SignatureOverrideCheck(SignatureOverrideCheck.Problem.DIFFERS, MESSAGE);
  }

  private static SignatureOverrideCheck renamed() {
    return new SignatureOverrideCheck(SignatureOverrideCheck.Problem.RENAMED, MESSAGE);
  }

  @Test
  void overridingWithFewerOrNewRequiredParametersDiffers() {
    final String base = "class A:\n    def f(self, a, b):\n        return a\n";
    assertThat(count(differs(), base + "class B(A):\n    def f(self, a):\n        return a\n"))
        .isEqualTo(1);
    assertThat(
            count(differs(), base + "class B(A):\n    def f(self, a, b, c):\n        return a\n"))
        .isEqualTo(1);
    assertThat(
            count(differs(), base + "class B(A):\n    def f(self, a, b, c=1):\n        return a\n"))
        .isZero();
  }

  @Test
  void droppingVariadicsDiffers() {
    final String base = "class A:\n    def f(self, *args, **kwargs):\n        return 1\n";
    assertThat(count(differs(), base + "class B(A):\n    def f(self):\n        return 1\n"))
        .isEqualTo(1);
    assertThat(
            count(
                differs(),
                base + "class B(A):\n    def f(self, *args, **kwargs):\n        return 1\n"))
        .isZero();
  }

  @Test
  void constructorsPrivateNamesAndSettersAreNotCompared() {
    assertThat(
            count(
                differs(),
                "class A:\n    def __init__(self, a):\n        pass\n"
                    + "class B(A):\n    def __init__(self):\n        pass\n"))
        .isZero();
    assertThat(
            count(
                differs(),
                "class A:\n    def __f(self, a):\n        pass\n"
                    + "class B(A):\n    def __f(self):\n        pass\n"))
        .isZero();
    assertThat(
            count(
                differs(),
                "class A:\n    @property\n    def v(self):\n        return 1\n"
                    + "class B(A):\n    @v.setter\n    def v(self, x, y):\n        pass\n"))
        .isZero();
  }

  @Test
  void specialMethodsKeepOnlyCountDifferences() {
    final String base = "class A:\n    def __eq__(self, a):\n        return True\n";
    assertThat(
            count(renamed(), base + "class B(A):\n    def __eq__(self, b):\n        return True\n"))
        .isZero();
    assertThat(
            count(
                differs(),
                base + "class B(A):\n    def __eq__(self, a, b):\n        return True\n"))
        .isEqualTo(1);
  }

  @Test
  void renamedParameterIsReportedUnlessItIsDummy() {
    final String base = "class A:\n    def f(self, value):\n        return 1\n";
    assertThat(count(renamed(), base + "class B(A):\n    def f(self, item):\n        return 1\n"))
        .isEqualTo(1);
    assertThat(count(renamed(), base + "class B(A):\n    def f(self, _item):\n        return 1\n"))
        .isZero();
    assertThat(count(differs(), base + "class B(A):\n    def f(self, item):\n        return 1\n"))
        .isZero();
  }

  @Test
  void staticMethodsAndOverloadsAreCompared() {
    assertThat(
            count(
                differs(),
                "class A:\n    @staticmethod\n    def f(a):\n        return a\n"
                    + "class B(A):\n    @staticmethod\n    def f():\n        return 1\n"))
        .isEqualTo(1);
    assertThat(
            count(
                differs(),
                "class A:\n    @staticmethod\n    def f(a):\n        return a\n"
                    + "class B(A):\n    @overload\n    def f(self):\n        return 1\n"))
        .isZero();
  }
}
