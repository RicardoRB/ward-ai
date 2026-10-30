package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Framework;
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
  static final String MUTMUT = "mutmut";
  static final String IMPORT_LINTER = "import-linter";
  static final String PYTEST_DJANGO = "pytest-django";
  static final String DJANGO_STUBS = "django-stubs";
  static final String HTTPX = "httpx";
  static final String PYTEST_FLASK = "pytest-flask";

  private static final Map<String, Package> PACKAGES =
      Map.ofEntries(
          Map.entry(RUFF, new Package(RUFF, "0.17.1")),
          Map.entry(MYPY, new Package(MYPY, "2.4.0")),
          Map.entry(PYTEST, new Package(PYTEST, "9.1.1")),
          Map.entry(COVERAGE, new Package(COVERAGE, "7.16.2")),
          Map.entry(PIP_AUDIT, new Package(PIP_AUDIT, "2.10.1")),
          Map.entry(MUTMUT, new Package(MUTMUT, "3.8.0")),
          Map.entry(IMPORT_LINTER, new Package(IMPORT_LINTER, "2.15")),
          Map.entry(PYTEST_DJANGO, new Package(PYTEST_DJANGO, "4.14.0")),
          Map.entry(DJANGO_STUBS, new Package(DJANGO_STUBS, "6.1.2")),
          Map.entry(HTTPX, new Package(HTTPX, "0.28.1")),
          Map.entry(PYTEST_FLASK, new Package(PYTEST_FLASK, "1.3.0")));

  /** The distributions each gate's command runs; pytest runs under coverage. */
  private static final Map<String, List<String>> GATE_TOOLS =
      Map.of(
          "ruff-format", List.of(RUFF),
          "ruff", List.of(RUFF),
          "mypy", List.of(MYPY),
          "pytest", List.of(PYTEST, COVERAGE),
          "coverage", List.of(COVERAGE),
          "pip-audit", List.of(PIP_AUDIT),
          "mutmut", List.of(MUTMUT),
          "import-linter", List.of(IMPORT_LINTER));

  /** The extra distributions Django projects need: the test plugin and the type stubs. */
  private static final Map<Framework, Map<String, List<String>>> FRAMEWORK_TOOLS =
      Map.of(
          Framework.DJANGO,
          Map.of("pytest", List.of(PYTEST_DJANGO), "mypy", List.of(DJANGO_STUBS)),
          Framework.FASTAPI,
          Map.of("pytest", List.of(HTTPX)),
          Framework.FLASK,
          Map.of("pytest", List.of(PYTEST_FLASK)));

  private PythonPackageCatalog() {}

  /**
   * Returns the pinned distributions the gates need that {@code declared} does not already name, in
   * gate order, each once. {@code declared} holds normalized names.
   */
  static List<Package> missing(final List<InitGateOption> gates, final Set<String> declared) {
    return missing(gates, declared, Framework.NONE);
  }

  /**
   * Returns the same as {@link #missing(List, Set)}, and for Django the packages its test and type
   * checks need: pytest-django and django-stubs.
   */
  static List<Package> missing(
      final List<InitGateOption> gates, final Set<String> declared, final Framework framework) {
    final Set<String> names = new LinkedHashSet<>();
    gates.forEach(gate -> names.addAll(GATE_TOOLS.getOrDefault(gate.id(), List.of())));
    final Map<String, List<String>> extra = FRAMEWORK_TOOLS.getOrDefault(framework, Map.of());
    gates.forEach(gate -> names.addAll(extra.getOrDefault(gate.id(), List.of())));
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
