package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The class-hierarchy, body-shape, unused-name and placeholder checks, parsed from Python source.
 */
final class ClassAndBodyChecksTest {

  private static final String MESSAGE = "m";
  private static final String BASE =
      "class Base:\n    def run(self, value=1):\n        return value\n\n\n";

  @Test
  void superInitNotCalledNeedsASameModuleBaseConstructor() {
    final String base = "class Base:\n    def __init__(self, v):\n        self.v = v\n\n\n";
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SUPER_INIT_NOT_CALLED,
                base + "class C(Base):\n    def __init__(self, v):\n        self.w = v\n"))
        .hasSize(1);
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SUPER_INIT_NOT_CALLED,
                base + "class C(Base):\n    def __init__(self, v):\n        super().__init__(v)\n"))
        .isEmpty();
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SUPER_INIT_NOT_CALLED,
                "from m import Base\n\n\nclass C(Base):\n    def __init__(self, v):\n        self.w = v\n"))
        .isEmpty();
  }

  @Test
  void signatureDiffersWhenTheOverrideHasFewerDefaults() {
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SIGNATURE_DIFFERS,
                BASE + "class C(Base):\n    def run(self, value):\n        return value\n"))
        .hasSize(1);
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SIGNATURE_DIFFERS,
                BASE + "class C(Base):\n    def run(self, value=2):\n        return value\n"))
        .isEmpty();
  }

  @Test
  void subclassingAFinalClassIsReported() {
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SUBCLASSED_FINAL,
                "from typing import final\n\n\n@final\nclass B:\n    pass\n\n\nclass C(B):\n    pass\n"))
        .hasSize(1);
    assertThat(
            hierarchy(
                ClassHierarchyCheck.Problem.SUBCLASSED_FINAL,
                "class B:\n    pass\n\n\nclass C(B):\n    pass\n"))
        .isEmpty();
  }

  @Test
  void raisingAClassWithoutBasesIsReported() {
    assertThat(run(new RaiseNonExceptionCheck(MESSAGE), "class P:\n    pass\n\n\nraise P()\n"))
        .hasSize(1);
    assertThat(
            run(
                new RaiseNonExceptionCheck(MESSAGE),
                "class P(Exception):\n    pass\n\n\nraise P()\n"))
        .isEmpty();
  }

  @Test
  void bodyShapesAreReportedOnlyInTheirOwnCases() {
    assertThat(
            run(
                new MethodBodyCheck(MethodBodyCheck.Problem.RETURN_IN_INIT, MESSAGE),
                "class C:\n    def __init__(self):\n        return 1\n"))
        .hasSize(1);
    assertThat(
            run(
                new MethodBodyCheck(MethodBodyCheck.Problem.RETURN_IN_INIT, MESSAGE),
                "class C:\n    def __init__(self):\n        return None\n"))
        .isEmpty();
    assertThat(
            run(
                new MethodBodyCheck(MethodBodyCheck.Problem.USELESS_PARENT_DELEGATION, MESSAGE),
                BASE
                    + "class C(Base):\n    def run(self, value=1):\n        return super().run(value)\n"))
        .hasSize(1);
    assertThat(
            run(
                new MethodBodyCheck(MethodBodyCheck.Problem.USED_PRIOR_GLOBAL, MESSAGE),
                "def f():\n    global g\n    g = 1\n"))
        .isEmpty();
  }

  @Test
  void unusedArgumentsSkipMethodsAndIgnoredNames() {
    assertThat(
            run(
                new UnusedNameCheck(UnusedNameCheck.Problem.UNUSED_ARGUMENT, MESSAGE),
                "def f(a, _b):\n    return 1\n"))
        .hasSize(1);
    assertThat(
            run(
                new UnusedNameCheck(UnusedNameCheck.Problem.UNUSED_ARGUMENT, MESSAGE),
                "class C:\n    def f(self, a):\n        return 1\n"))
        .isEmpty();
  }

  @Test
  void placeholdersAreReportedOnlyNextToOtherStatements() {
    assertThat(
            run(
                new StatementListCheck(StatementListCheck.Problem.UNNECESSARY_PASS, MESSAGE),
                "def f():\n    \"\"\"d\"\"\"\n    pass\n"))
        .hasSize(1);
    assertThat(
            run(
                new StatementListCheck(StatementListCheck.Problem.UNNECESSARY_PASS, MESSAGE),
                "def f():\n    pass\n"))
        .isEmpty();
  }

  private static List<RuleMatch> hierarchy(
      final ClassHierarchyCheck.Problem problem, final String source) {
    return new ClassHierarchyCheck(problem, MESSAGE).matches(tree(source));
  }

  private static List<RuleMatch> run(final RuleCheck check, final String source) {
    return check.matches(tree(source));
  }

  private static SyntaxNode tree(final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source))
        .tree();
  }
}
