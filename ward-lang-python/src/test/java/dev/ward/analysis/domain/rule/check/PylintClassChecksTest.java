package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/**
 * Edge cases of the Pylint class checks: abstract methods, instantiation and the method resolution
 * order.
 */
final class PylintClassChecksTest {

  private static final String MESSAGE = "message";

  private static final String ABSTRACT_BASE =
      "from abc import ABC, abstractmethod\n"
          + "class Base(ABC):\n"
          + "    @abstractmethod\n"
          + "    def run(self):\n"
          + "        ...\n";

  private static SyntaxNode root(final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source))
        .tree();
  }

  private static int count(final RuleCheck check, final String source) {
    return check.matches(root(source)).size();
  }

  @Test
  void instantiatingAnAbstractClassWithoutOverrideIsReported() {
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.INSTANTIATED, MESSAGE),
                ABSTRACT_BASE + "b = Base()\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.INSTANTIATED, MESSAGE),
                ABSTRACT_BASE + "class C(Base):\n    def run(self):\n        return 1\nc = C()\n"))
        .isZero();
  }

  @Test
  void instantiationInsideTheClassOrOfANonAbcClassIsNotReported() {
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.INSTANTIATED, MESSAGE),
                ABSTRACT_BASE + "class D:\n    def make(self):\n        return Base()\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.INSTANTIATED, MESSAGE),
                ABSTRACT_BASE + "    def make(self):\n        return Base()\n"))
        .isZero();
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.INSTANTIATED, MESSAGE),
                "from abc import abstractmethod\nclass P:\n"
                    + "    @abstractmethod\n    def run(self):\n        ...\nx = P()\n"))
        .isZero();
  }

  @Test
  void unknownBaseMakesTheClassUnchecked() {
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.INSTANTIATED, MESSAGE),
                "from abc import ABC, abstractmethod\nfrom m import Other\n"
                    + "class B(ABC, Other):\n    @abstractmethod\n    def run(self):\n        ...\n"
                    + "b = B()\n"))
        .isZero();
  }

  @Test
  void inheritedAbstractMethodMustBeOverriddenByAConcreteClass() {
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.METHOD_NOT_OVERRIDDEN, MESSAGE),
                ABSTRACT_BASE + "class Child(Base):\n    pass\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.METHOD_NOT_OVERRIDDEN, MESSAGE),
                ABSTRACT_BASE + "class Child(Base, ABC):\n    pass\n"))
        .isZero();
    assertThat(
            count(
                new AbstractClassCheck(AbstractClassCheck.Problem.METHOD_NOT_OVERRIDDEN, MESSAGE),
                ABSTRACT_BASE + "class Child(Base):\n    def run(self):\n        return 2\n"))
        .isZero();
  }

  @Test
  void inconsistentMroIsReportedAndAnUnknownBaseIsNot() {
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.INCONSISTENT_MRO, MESSAGE),
                "class A:\n    pass\nclass B(A):\n    pass\nclass C(A, B):\n    pass\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.INCONSISTENT_MRO, MESSAGE),
                "class C(Unknown, object):\n    pass\n"))
        .isZero();
  }

  @Test
  void literalOrFunctionBaseIsNotAClass() {
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.NON_CLASS_BASE, MESSAGE),
                "class A(3):\n    pass\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.NON_CLASS_BASE, MESSAGE),
                "def f():\n    pass\nclass A(f):\n    pass\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.NON_CLASS_BASE, MESSAGE),
                "K = 'x'\nclass A(K):\n    pass\n"))
        .isEqualTo(1);
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.NON_CLASS_BASE, MESSAGE),
                "K = 1\nK = 2\nclass A(K):\n    pass\n"))
        .isZero();
    assertThat(
            count(
                new ClassBaseCheck(ClassBaseCheck.Problem.NON_CLASS_BASE, MESSAGE),
                "class B:\n    pass\nclass A(B):\n    pass\n"))
        .isZero();
  }
}
