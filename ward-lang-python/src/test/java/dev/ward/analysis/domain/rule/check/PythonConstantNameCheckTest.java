package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.scoped;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The constant-name check reports a module name that is assigned once and is not UPPER_CASE. */
final class PythonConstantNameCheckTest {

  private static final PythonConstantNameCheck CHECK = new PythonConstantNameCheck("not upper");

  @Test
  void singleAssignmentNotUpperCaseIsReported() {
    assertThat(positions(scoped(CHECK, "once = 2\n"))).containsExactly("1:1");
  }

  @Test
  void reassignedNameIsAVariableAndIsNotReported() {
    assertThat(positions(scoped(CHECK, "count = 0\ncount = 1\n"))).isEmpty();
  }

  @Test
  void upperCaseAndDunderNamesAreNotReported() {
    assertThat(positions(scoped(CHECK, "MAX_SIZE = 10\n__version__ = '1'\n"))).isEmpty();
  }

  @Test
  void functionsAndClassesAreNotReported() {
    assertThat(positions(scoped(CHECK, "def run():\n    pass\nclass Box:\n    pass\n"))).isEmpty();
  }
}
