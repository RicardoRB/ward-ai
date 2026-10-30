package dev.ward.infrastructure.init;

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
    assertThat(PythonPackageCatalog.missing(gates("ruff", "mypy"), Set.of("ruff")))
        .extracting(PythonPackageCatalog.Package::requirement)
        .containsExactly("mypy==2.4.0");
  }

  @Test
  void everyStandardGateHasAnExactPin() {
    final List<InitGateOption> gates =
        gates("ruff-format", "ruff", "mypy", "pytest", "coverage", "pip-audit");

    assertThat(PythonPackageCatalog.missing(gates, Set.of()))
        .extracting(PythonPackageCatalog.Package::requirement)
        .containsExactlyInAnyOrder(
            "ruff==0.17.1",
            "mypy==2.4.0",
            "pytest==9.1.1",
            "coverage==7.16.2",
            "pip-audit==2.10.1");
  }

  private static List<InitGateOption> gates(final String... ids) {
    return java.util.Arrays.stream(ids)
        .map(id -> new InitGateOption(id, List.of(), true, ""))
        .toList();
  }
}
