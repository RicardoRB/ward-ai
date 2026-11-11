package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/** Edge cases of the optional Pylint extension checks, parsed from inline source. */
final class PylintOptionalChecksTest {

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
  void aliasAndBaseClassOverlapInOneExceptTuple() {
    final RuleCheck check = new OverlappingExceptCheck(MESSAGE);
    assertThat(count(check, "try:\n    pass\nexcept (OSError, IOError):\n    pass\n")).isOne();
    assertThat(count(check, "try:\n    pass\nexcept (Exception, KeyError):\n    pass\n")).isOne();
    assertThat(count(check, "try:\n    pass\nexcept (KeyError, ValueError):\n    pass\n")).isZero();
    assertThat(count(check, "try:\n    pass\nexcept OSError:\n    pass\n")).isZero();
  }

  @Test
  void classWithEqAndHashIsFine() {
    final RuleCheck check =
        new ClassProtocolCheck(ClassProtocolCheck.Problem.EQ_WITHOUT_HASH, MESSAGE);
    assertThat(count(check, "class A:\n    def __eq__(self, o):\n        return True\n")).isOne();
    assertThat(
            count(
                check,
                "class A:\n    __hash__ = None\n\n    def __eq__(self, o):\n        return True\n"))
        .isZero();
  }

  @Test
  void assignmentThenIfOnTheSameNameIsReported() {
    final RuleCheck check =
        new ConsecutiveStatementsCheck(ConsecutiveStatementsCheck.Problem.ASSIGNMENT_EXPR, MESSAGE);
    assertThat(count(check, "x = f()\nif x is not None:\n    pass\n")).isOne();
    assertThat(count(check, "x = f()\nif not x:\n    pass\n")).isOne();
    assertThat(count(check, "x = f()\nif xs:\n    pass\n")).isZero();
    assertThat(count(check, "x = f()\ny = 1\nif x:\n    pass\n")).isZero();
  }

  @Test
  void elifAfterABranchEndingWithIfIsReported() {
    final RuleCheck check =
        new ConsecutiveStatementsCheck(ConsecutiveStatementsCheck.Problem.CONFUSING_ELIF, MESSAGE);
    assertThat(count(check, "if a:\n    if b:\n        pass\nelif c:\n    pass\n")).isOne();
    assertThat(
            count(check, "if a:\n    pass\nelif c:\n    if d:\n        pass\nelif e:\n    pass\n"))
        .isOne();
    assertThat(count(check, "if a:\n    if b:\n        pass\n    x = 1\nelif c:\n    pass\n"))
        .isZero();
  }

  @Test
  void tryBodyCountsOnlyItsOwnStatements() {
    final RuleCheck check = new TryBodyLengthCheck(2, "{value} of {max}");
    assertThat(check.matches(root("try:\n    a()\n    b()\n    c()\nexcept E:\n    pass\n")))
        .singleElement()
        .satisfies(match -> assertThat(match.message()).isEqualTo("3 of 2"));
    assertThat(count(check, "try:\n    a()\n    b()\nexcept E:\n    a()\n    b()\n    c()\n"))
        .isZero();
    assertThat(count(check, "try:\n    if a:\n        b()\n        c()\nexcept E:\n    pass\n"))
        .isOne();
  }
}
