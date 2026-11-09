package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.tree;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.check.PythonMetaclassArgumentCheck.Problem;
import org.junit.jupiter.api.Test;

/** The metaclass check names the first parameter of a metaclass method and class method. */
final class PythonMetaclassArgumentCheckTest {

  private static java.util.List<String> run(final Problem problem, final String source) {
    return positions(new PythonMetaclassArgumentCheck(problem, "wrong name").matches(tree(source)));
  }

  @Test
  void instanceMethodOfAMetaclassMustTakeCls() {
    assertThat(run(Problem.METHOD, "class Meta(type):\n    def make(self):\n        pass\n"))
        .containsExactly("2:5");
    assertThat(run(Problem.METHOD, "class Meta(type):\n    def make(cls):\n        pass\n"))
        .isEmpty();
  }

  @Test
  void classMethodOfAMetaclassMustTakeMcs() {
    final String wrong = "class Meta(type):\n    @classmethod\n    def make(self):\n        pass\n";
    assertThat(run(Problem.CLASSMETHOD, wrong)).containsExactly("3:5");
    final String right = "class Meta(type):\n    @classmethod\n    def make(mcs):\n        pass\n";
    assertThat(run(Problem.CLASSMETHOD, right)).isEmpty();
  }

  @Test
  void staticMethodsAreSkipped() {
    final String source = "class Meta(type):\n    @staticmethod\n    def make(x):\n        pass\n";
    assertThat(run(Problem.METHOD, source)).isEmpty();
    assertThat(run(Problem.CLASSMETHOD, source)).isEmpty();
  }

  @Test
  void classesWithoutAMetaclassBaseAreNotChecked() {
    assertThat(run(Problem.METHOD, "class Plain(Base):\n    def make(self): pass\n")).isEmpty();
  }

  @Test
  void abcMetaAndNamesEndingInMetaAreMetaclasses() {
    assertThat(run(Problem.METHOD, "import abc\nclass A(abc.ABCMeta):\n    def m(self): pass\n"))
        .containsExactly("3:5");
    assertThat(run(Problem.METHOD, "class B(SomeMeta):\n    def m(self): pass\n"))
        .containsExactly("2:5");
  }

  @Test
  void methodWithoutParametersIsSkipped() {
    assertThat(run(Problem.METHOD, "class Meta(type):\n    def make():\n        pass\n")).isEmpty();
  }
}
