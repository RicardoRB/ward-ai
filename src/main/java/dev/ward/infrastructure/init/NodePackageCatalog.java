package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The dev dependencies init adds for the Node gates, pinned to the newest releases at least 14 days
 * old on 2026-10-24, with the Node and TypeScript versions each supports. A gate's packages may
 * depend on the project's language.
 */
final class NodePackageCatalog {
  static final String TYPESCRIPT = "typescript";
  static final String TYPESCRIPT_ESLINT = "typescript-eslint";
  private static final String ESLINT = "eslint";
  private static final String ESLINT_JS = "@eslint/js";
  private static final String GLOBALS = "globals";
  private static final String ESLINT_NODE = "^20.19.0 || ^22.13.0 || >=24";
  private static final String VITEST_NODE = "^22.12.0 || ^24.0.0 || >=26.0.0";
  private static final String VITEST_VERSION = "5.0.3";

  private static final Map<String, Package> PACKAGES =
      Map.of(
          ESLINT,
          new Package(ESLINT, "10.12.0", ESLINT_NODE, null),
          ESLINT_JS,
          new Package(ESLINT_JS, "10.0.1", ESLINT_NODE, null),
          GLOBALS,
          new Package(GLOBALS, "17.13.0", ">=18", null),
          TYPESCRIPT_ESLINT,
          new Package(
              TYPESCRIPT_ESLINT, "8.71.1", "^18.18.0 || ^20.9.0 || >=21.1.0", ">=4.8.4 <6.1.0"),
          "prettier",
          new Package("prettier", "3.9.10", ">=14", null),
          "vitest",
          new Package("vitest", VITEST_VERSION, VITEST_NODE, null),
          "@vitest/coverage-v8",
          new Package("@vitest/coverage-v8", VITEST_VERSION, VITEST_NODE, null),
          // The newest release inside typescript-eslint's range; 7.0.2 is outside it.
          TYPESCRIPT,
          new Package(TYPESCRIPT, "6.0.3", ">=14.17", null));

  // typescript-eslint needs the compiler as a peer, so ESLint adds it when it is missing.
  private static final Map<String, List<String>> GATES =
      Map.of(
          "tsc",
          List.of(TYPESCRIPT),
          ESLINT,
          List.of(ESLINT, ESLINT_JS, TYPESCRIPT_ESLINT, TYPESCRIPT),
          "prettier",
          List.of("prettier"),
          "vitest",
          List.of("vitest", "@vitest/coverage-v8"),
          "jest",
          List.of(),
          "bun-test",
          List.of());

  /** Rows that replace a {@link #GATES} row for one language; other gates are shared. */
  private static final Map<Language, Map<String, List<String>>> LANGUAGE_GATES =
      Map.of(Language.JAVASCRIPT, Map.of(ESLINT, List.of(ESLINT, ESLINT_JS, GLOBALS)));

  /**
   * One pinned package.
   *
   * @param name the npm package name
   * @param version the exact version init adds
   * @param node the Node versions the release supports
   * @param typescript the TypeScript versions it supports, or {@code null} when it has no limit
   */
  record Package(String name, String version, String node, String typescript) {}

  private NodePackageCatalog() {}

  /** Returns the pinned package {@code name}, if the catalog has it. */
  static Optional<Package> find(final String name) {
    return Optional.ofNullable(PACKAGES.get(name));
  }

  /** Returns the Node gates the catalog covers, for the contract test. */
  static Set<String> gates() {
    return GATES.keySet();
  }

  /**
   * Returns the packages {@code gate} needs in a {@code language} project, empty for a gate that is
   * not a Node tool.
   */
  static List<String> packages(final Language language, final String gate) {
    return LANGUAGE_GATES
        .getOrDefault(language, Map.of())
        .getOrDefault(gate, GATES.getOrDefault(gate, List.of()));
  }

  /**
   * Returns the packages {@code gates} need in a {@code language} project that {@code manifest}
   * does not declare, each once, in gate order.
   */
  static List<Package> missing(
      final Language language, final List<InitGateOption> gates, final PackageManifest manifest) {
    final Set<String> names = new LinkedHashSet<>();
    gates.forEach(gate -> names.addAll(packages(language, gate.id())));
    return names.stream().filter(name -> !manifest.declares(name)).map(PACKAGES::get).toList();
  }
}
