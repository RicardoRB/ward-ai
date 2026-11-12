package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A {@code break} or {@code continue} outside a loop must not abort the lowering of the unit. */
final class PythonJumpOutsideLoopTest {

  @ParameterizedTest
  @ValueSource(strings = {"break", "continue"})
  void jumpAtModuleLevelStillBuildsTheGraphs(final String jump) {
    final ControlFlowResult result = lower("def f():\n    pass\n\n" + jump + "\n");

    assertThat(bodies(result)).contains("f");
  }

  @ParameterizedTest
  @ValueSource(strings = {"break", "continue"})
  void jumpInsideAFunctionOutsideALoopLeavesTheBodyAndSiblingsStillLower(final String jump) {
    final ControlFlowResult result =
        lower("def bad():\n    " + jump + "\n\ndef good():\n    x()\n");

    assertThat(bodies(result)).contains("bad", "good");
    assertThat(result.failures()).isEmpty();
  }

  @Test
  void jumpAfterALoopHasNoLoopToTarget() {
    final ControlFlowResult result =
        lower("def f(items):\n    for i in items:\n        pass\n    break\n");

    assertThat(bodies(result)).contains("f");
  }

  private static ControlFlowResult lower(final String source) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    return new PythonControlFlow().lower(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  private static List<String> bodies(final ControlFlowResult result) {
    return result.graphs().stream().map(ControlFlowGraph::body).toList();
  }
}
