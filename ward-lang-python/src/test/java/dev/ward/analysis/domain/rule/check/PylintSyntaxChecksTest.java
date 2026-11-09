package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/**
 * Edge cases of the Pylint-derived checks on declarations, calls and loops, parsed from inline
 * source.
 */
final class PylintSyntaxChecksTest {

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
  void fieldInsideMakeDataclassIsValid() {
    assertThat(
            count(
                new FieldCallCheck(MESSAGE),
                "from dataclasses import field, make_dataclass\n"
                    + "X = make_dataclass(\"X\", [(\"a\", int, field(default=1))])\n"))
        .isZero();
  }

  @Test
  void keywordOnlyParameterWithDefaultIsNotMissing() {
    assertThat(
            count(
                new SameModuleCallCheck(SameModuleCallCheck.Problem.MISSING_KWOA, MESSAGE),
                "def f(a, *, b=2):\n    return a\n\n\nf(1)\n"))
        .isZero();
  }

  @Test
  void positionalOnlyParameterPassedByKeywordIsReported() {
    assertThat(
            count(
                new SameModuleCallCheck(SameModuleCallCheck.Problem.POSITIONAL_ONLY, MESSAGE),
                "def f(a, /, b):\n    return a\n\n\nf(a=1, b=2)\n"))
        .isEqualTo(1);
  }

  @Test
  void nonlocalBoundByAParameterOfTheEnclosingFunctionIsBound() {
    assertThat(
            count(
                new NonlocalCheck(MESSAGE),
                "def f(y):\n    def g():\n        nonlocal y\n        return y\n    return g\n"))
        .isZero();
  }

  @Test
  void enumExtensionOfAnEnumWithoutMembersIsValid() {
    assertThat(
            count(
                new ClassProtocolCheck(ClassProtocolCheck.Problem.INVALID_ENUM_EXTENSION, MESSAGE),
                "from enum import Enum\n\n\nclass C(Enum):\n    def m(self):\n        x = 1\n\n\n"
                    + "class D(C):\n    pass\n"))
        .isZero();
  }
}
