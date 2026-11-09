package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.tree;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.check.PythonDocstringCheck.Problem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The docstring check reports parameters that the Args section of a docstring does not document.
 */
final class PythonDocstringArgsCheckTest {

  private static List<String> run(final String source) {
    return positions(
        new PythonDocstringCheck(Problem.UNDOCUMENTED_PARAM, "undocumented").matches(tree(source)));
  }

  @Test
  void undocumentedParamIsReportedOncePerMissingParameter() {
    final String source =
        "def run(a, b, c):\n    '''Run.\n\n    Args:\n        a: first.\n    '''\n";
    assertThat(run(source)).containsExactly("2:5", "2:5");
  }

  @Test
  void selfClsAndStarArgumentsAreHandled() {
    final String source =
        "class A:\n    def f(self, *args):\n        '''F.\n\n"
            + "        Args:\n            *args: items.\n        '''\n";
    assertThat(run(source)).isEmpty();
  }

  @Test
  void noArgsSectionMeansNothingIsUndocumented() {
    assertThat(run("def run(a):\n    '''Run.'''\n")).isEmpty();
  }

  @Test
  void continuationLinesDoNotCountAsEntries() {
    final String source =
        "def run(a, more):\n    '''Run.\n\n    Args:\n        a: first,\n"
            + "            more: part.\n    '''\n";
    assertThat(run(source)).containsExactly("2:5");
  }

  @Test
  void functionWithoutDocstringHasNoUndocumentedParams() {
    assertThat(run("def run(a):\n    return a\n")).isEmpty();
  }
}
