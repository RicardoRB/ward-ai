package dev.ward.infrastructure.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The pinned tools init adds for the Python gates, and which of them a project already has. */
class PythonPackageCatalogTest {
  @Test
  void pytestNeedsCoverageAsWell() {
    assertThat(PythonPackageCatalog.missing(gates("pytest"), Set.of()))
        .extracting(PythonPackageCatalog.Package::requirement)
        .containsExactly("pytest==9.1.1", "coverage==7.16.2");
  }

  @Test
  void declaredToolsAreNotAddedAgain() {
    assertThat(PythonPackageCatalog.missing(gates("mypy", "coverage"), Set.of("mypy")))
        .extracting(PythonPackageCatalog.Package::requirement)
        .containsExactly("coverage==7.16.2");
  }

  @Test
  void everyStandardGateHasAnExactPin() {
    final List<InitGateOption> gates = gates("mypy", "pytest", "coverage");

    assertThat(PythonPackageCatalog.missing(gates, Set.of()))
        .extracting(PythonPackageCatalog.Package::requirement)
        .containsExactlyInAnyOrder("mypy==2.4.0", "pytest==9.1.1", "coverage==7.16.2");
  }

  private static List<InitGateOption> gates(final String... ids) {
    return java.util.Arrays.stream(ids)
        .map(id -> new InitGateOption(id, List.of(), true, ""))
        .toList();
  }
}
