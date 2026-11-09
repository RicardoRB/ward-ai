package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.scoped;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The redefined-name check reports a function's local or parameter that hides a module name. */
final class PythonRedefinedNameCheckTest {

  private static final PythonRedefinedNameCheck CHECK = new PythonRedefinedNameCheck("hides");

  @Test
  void parameterHidingAModuleNameIsReported() {
    final String source = "name = 1\ndef run(name):\n    return name\n";
    assertThat(positions(scoped(CHECK, source))).containsExactly("2:9");
  }

  @Test
  void localHidingAModuleNameIsReported() {
    final String source = "x = 1\ndef f():\n    x = 2\n    return x\n";
    assertThat(positions(scoped(CHECK, source))).containsExactly("3:5");
  }

  @Test
  void globalDeclarationIsExempt() {
    final String source = "x = 1\ndef f():\n    global x\n    x = 2\n";
    assertThat(positions(scoped(CHECK, source))).isEmpty();
  }

  @Test
  void namesWithoutAModuleBindingAreNotReported() {
    assertThat(positions(scoped(CHECK, "def f(a):\n    return a\n"))).isEmpty();
  }
}
