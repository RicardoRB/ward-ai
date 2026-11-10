package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.tree;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.check.PythonDocstringCheck.Problem;
import org.junit.jupiter.api.Test;

/**
 * The docstring check reports each problem where pylint and pydocstyle do, and exempts private
 * names.
 */
final class PythonDocstringCheckTest {

  private static final String MESSAGE = "docstring problem";

  private static java.util.List<String> run(final Problem problem, final String source) {
    return positions(new PythonDocstringCheck(problem, MESSAGE).matches(tree(source)));
  }

  @Test
  void missingModuleDocstringIsReportedAtTheFirstStatement() {
    assertThat(run(Problem.MISSING_MODULE, "import os\n")).containsExactly("1:1");
  }

  @Test
  void moduleWithDocstringOrNoStatementsIsNotReported() {
    assertThat(run(Problem.MISSING_MODULE, "'''Module.'''\nimport os\n")).isEmpty();
    assertThat(run(Problem.MISSING_MODULE, "")).isEmpty();
  }

  @Test
  void missingClassDocstringSkipsPrivateClasses() {
    assertThat(run(Problem.MISSING_CLASS, "class Box:\n    pass\nclass _Hidden:\n    pass\n"))
        .containsExactly("1:1");
    assertThat(run(Problem.MISSING_CLASS, "class Box:\n    '''A box.'''\n    pass\n")).isEmpty();
  }

  @Test
  void missingFunctionDocstringIsReportedForMethodsAndSkipsPrivateFunctions() {
    final String source =
        "def run():\n    return 1\nclass A:\n    def f(self):\n        pass\n"
            + "def _hidden():\n    return 2\n";
    assertThat(run(Problem.MISSING_FUNCTION, source)).containsExactly("1:1", "4:5");
  }

  @Test
  void blankDocstringIsEmpty() {
    assertThat(run(Problem.EMPTY, "def run():\n    '''   '''\n    return 1\n"))
        .containsExactly("2:5");
    assertThat(run(Problem.EMPTY, "def run():\n    ''\n    return 1\n")).containsExactly("2:5");
    assertThat(run(Problem.EMPTY, "def run():\n    '''Run.'''\n    return 1\n")).isEmpty();
  }

  @Test
  void trailingPeriodIsCheckedOnTheSummaryLine() {
    assertThat(run(Problem.TRAILING_PERIOD, "def a():\n    '''Summary'''\n"))
        .containsExactly("2:5");
    assertThat(run(Problem.TRAILING_PERIOD, "def b():\n    '''Summary.'''\n")).isEmpty();
    assertThat(run(Problem.TRAILING_PERIOD, "def c():\n    '''Summary\n    last.'''\n"))
        .containsExactly("2:5");
    assertThat(run(Problem.TRAILING_PERIOD, "def d():\n    '''\n    Summary.\n    '''\n"))
        .isEmpty();
  }
}
