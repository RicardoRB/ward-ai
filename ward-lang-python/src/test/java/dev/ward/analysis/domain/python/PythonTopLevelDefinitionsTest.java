package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.SummaryFixtures.names;
import static dev.ward.analysis.domain.python.SummaryFixtures.summarize;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Symbol;
import org.junit.jupiter.api.Test;

/** Checks the declared definitions and the used names that the unused-definition rules read. */
final class PythonTopLevelDefinitionsTest {

  @Test
  void declaresUndecoratedTopLevelFunctionsAndClassesWithTheirKind() {
    final FileSummary summary =
        summarize(
            "def f():\n    def inner():\n        pass\n\n@deco\ndef g():\n    pass\n\n"
                + "class C:\n    def m(self):\n        pass\n\nx = 1\n");

    assertThat(names(summary.declared())).containsExactly("f", "C", "m");
    assertThat(summary.declared())
        .extracting(Symbol::kind)
        .containsExactly("function", "class", "method");
  }

  @Test
  void usedNamesSkipDefinitionNamesButKeepUsesAndAllEntries() {
    final FileSummary summary =
        summarize(
            "__all__ = ['listed']\n\ndef f(arg):\n    return helper(arg)\n\nclass C(Base):\n    pass\n");

    assertThat(summary.usedNames()).contains("helper", "arg", "Base", "listed");
    assertThat(summary.usedNames()).doesNotContain("f", "C");
  }
}
