package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Maps file paths to module names through the deepest containing source root. */
final class PythonProjectLayoutTest {

  @Test
  void mapsASrcLayoutFileToItsDottedModule() {
    final PythonProjectLayout layout = new PythonProjectLayout(List.of("src/"));

    assertThat(layout.moduleOf("src/shop/repository/store.py"))
        .contains(new PythonModule("shop.repository.store", false));
  }

  @Test
  void mapsAnInitFileToItsPackage() {
    final PythonProjectLayout layout = new PythonProjectLayout(List.of("src/"));

    assertThat(layout.moduleOf("src/shop/__init__.py")).contains(new PythonModule("shop", true));
  }

  @Test
  void choosesTheDeepestRootThatContainsTheFile() {
    final PythonProjectLayout layout =
        new PythonProjectLayout(List.of("", "packages/billing/src/"));

    assertThat(layout.moduleOf("packages/billing/src/billing/invoice.py"))
        .contains(new PythonModule("billing.invoice", false));
  }

  @Test
  void hasNoModuleOutsideEveryRoot() {
    final PythonProjectLayout layout = new PythonProjectLayout(List.of("src/"));

    assertThat(layout.moduleOf("tests/test_price.py")).isEmpty();
  }

  @Test
  void hasNoModuleForADirectoryThatIsNotAnIdentifier() {
    final PythonProjectLayout layout = new PythonProjectLayout(List.of(""));

    assertThat(layout.moduleOf("my-tool/run.py")).isEmpty();
  }

  @Test
  void hasNoModuleForANonPythonFile() {
    final PythonProjectLayout layout = new PythonProjectLayout(List.of(""));

    assertThat(layout.moduleOf("shop/stubs.pyi")).isEmpty();
  }

  @Test
  void rejectsARootThatDoesNotEndWithASeparator() {
    assertThatThrownBy(() -> new PythonProjectLayout(List.of("src")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
