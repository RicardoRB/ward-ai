package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The dev tools init pins for the Python gates, at exact versions. The newest release at least 14
 * days old on 2026-10-30, each with the Python range it declares; the matrix (#139) proves them on
 * every supported version.
 */
final class PythonPackageCatalog {
  static final String RUFF = "ruff";
  static final String MYPY = "mypy";
  static final String PYTEST = "pytest";
  static final String COVERAGE = "coverage";
  static final String PIP_AUDIT = "pip-audit";

  private static final Map<String, Package> PACKAGES =
      Map.of(
          RUFF, new Package(RUFF, "0.17.1"),
          MYPY, new Package(MYPY, "2.4.0"),
          PYTEST, new Package(PYTEST, "9.1.1"),
          COVERAGE, new Package(COVERAGE, "7.16.2"),
          PIP_AUDIT, new Package(PIP_AUDIT, "2.10.1"));

  /** The distributions each gate's command runs; pytest runs under coverage. */
  private static final Map<String, List<String>> GATE_TOOLS =
      Map.of(
          "ruff-format", List.of(RUFF),
          "ruff", List.of(RUFF),
          "mypy", List.of(MYPY),
          "pytest", List.of(PYTEST, COVERAGE),
          "coverage", List.of(COVERAGE),
          "pip-audit", List.of(PIP_AUDIT));

  private PythonPackageCatalog() {}

  /**
   * Returns the pinned distributions the gates need that {@code declared} does not already name, in
   * gate order, each once. {@code declared} holds normalized names.
   */
  static List<Package> missing(final List<InitGateOption> gates, final Set<String> declared) {
    final Set<String> names = new LinkedHashSet<>();
    gates.forEach(gate -> names.addAll(GATE_TOOLS.getOrDefault(gate.id(), List.of())));
    return names.stream()
        .filter(name -> !declared.contains(name))
        .map(PACKAGES::get)
        .filter(Objects::nonNull)
        .toList();
  }

  /** One pinned distribution. */
  record Package(String name, String version) {
    /** Returns the PEP 508 requirement that pins this distribution. */
    String requirement() {
      return name + "==" + version;
    }
  }
}
