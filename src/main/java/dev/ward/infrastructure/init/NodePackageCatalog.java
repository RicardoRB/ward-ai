package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  static final String ESLINT = "eslint";
  static final String ESLINT_VERSION = "10.12.0";
  private static final String ESLINT_JS = "@eslint/js";
  private static final String GLOBALS = "globals";
  private static final String ESLINT_NODE = "^20.19.0 || ^22.13.0 || >=24";
  private static final String VITEST_NODE = "^20.0.0 || ^22.0.0 || >=24.0.0";
  private static final String VITEST_VERSION = "4.0.18";
  private static final String STRYKER = "stryker";
  private static final String STRYKER_CORE = "@stryker-mutator/core";
  private static final String STRYKER_VERSION = "10.1.1";
  private static final String STRYKER_NODE = ">=22.0.0";
  private static final String DEPENDENCY_CRUISER = "dependency-cruiser";
  private static final String DEPENDENCY_CRUISER_VERSION = "18.6.0";
  private static final String DEPENDENCY_CRUISER_NODE = "^22.0.0 || ^24.0.0 || >=26.0.0";
  private static final String DEPENDENCY_CRUISER_TYPESCRIPT = ">=2.0.0 <7.0.0";
  private static final String BIOME = "biome";
  static final String BIOME_PACKAGE = "@biomejs/biome";
  static final String BIOME_VERSION = "2.5.15";
  private static final String BIOME_NODE = ">=14.21.3";

  /** The Biome releases with the checkstyle reporter; 3.x may change the config schema. */
  static final String BIOME_SUPPORTED = ">=2.3.0 <3.0.0";

  /** The ESLint releases with {@code eslint/config}, which the generated config imports. */
  static final String ESLINT_SUPPORTED = ">=9.22.0 <11.0.0";

  /** Stryker's runner plugin for each test gate; Bun's command runner is built into the core. */
  private static final Map<String, String> STRYKER_RUNNERS =
      Map.of("vitest", "@stryker-mutator/vitest-runner", "jest", "@stryker-mutator/jest-runner");

  private static final Map<String, Package> PACKAGES =
      Map.ofEntries(
          entry(new Package(ESLINT, ESLINT_VERSION, ESLINT_NODE, null)),
          entry(new Package(ESLINT_JS, "10.0.1", ESLINT_NODE, null)),
          entry(new Package(GLOBALS, "17.13.0", ">=18", null)),
          entry(
              new Package(
                  TYPESCRIPT_ESLINT,
                  "8.71.1",
                  "^18.18.0 || ^20.9.0 || >=21.1.0",
                  ">=4.8.4 <6.1.0")),
          entry(new Package("prettier", "3.9.10", ">=14", null)),
          entry(new Package(BIOME_PACKAGE, BIOME_VERSION, BIOME_NODE, null)),
          entry(new Package("vitest", VITEST_VERSION, VITEST_NODE, null)),
          entry(new Package("@vitest/coverage-v8", VITEST_VERSION, VITEST_NODE, null)),
          // The newest release inside typescript-eslint's range; 7.0.2 is outside it.
          entry(new Package(TYPESCRIPT, "6.0.3", ">=14.17", null)),
          // Younger than 14 days on 2026-10-24, but 10.0.0's Vitest runner kills no mutant on
          // Vitest 5, so every strict run scored 0. 10.1.1 supports Vitest 4 and 5.
          entry(new Package(STRYKER_CORE, STRYKER_VERSION, STRYKER_NODE, null)),
          entry(new Package(STRYKER_RUNNERS.get("vitest"), STRYKER_VERSION, STRYKER_NODE, null)),
          entry(new Package(STRYKER_RUNNERS.get("jest"), STRYKER_VERSION, STRYKER_NODE, null)),
          entry(
              new Package(
                  DEPENDENCY_CRUISER,
                  DEPENDENCY_CRUISER_VERSION,
                  DEPENDENCY_CRUISER_NODE,
                  DEPENDENCY_CRUISER_TYPESCRIPT)));

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
          List.of(),
          STRYKER,
          List.of(STRYKER_CORE),
          DEPENDENCY_CRUISER,
          List.of(DEPENDENCY_CRUISER, TYPESCRIPT),
          BIOME,
          List.of(BIOME_PACKAGE));

  /** Rows that replace a {@link #GATES} row for one language; other gates are shared. */
  private static final Map<Language, Map<String, List<String>>> LANGUAGE_GATES =
      Map.of(
          Language.JAVASCRIPT,
          Map.of(
              ESLINT,
              List.of(ESLINT, ESLINT_JS, GLOBALS),
              DEPENDENCY_CRUISER,
              // The parser reads .js without the compiler.
              List.of(DEPENDENCY_CRUISER)));

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

  private static Map.Entry<String, Package> entry(final Package tool) {
    return Map.entry(tool.name(), tool);
  }

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
    if (names.contains(STRYKER_CORE)) {
      gates.stream()
          .map(gate -> STRYKER_RUNNERS.get(gate.id()))
          .filter(Objects::nonNull)
          .forEach(names::add);
    }
    return names.stream()
        .filter(name -> !manifest.declares(name))
        .map(name -> pinned(name, manifest))
        .toList();
  }

  /**
   * Returns the catalog pin of {@code name}. @eslint/js ships with ESLint, so when the project
   * declares ESLint it takes the declared range; a pin outside that range fails the install.
   */
  private static Package pinned(final String name, final PackageManifest manifest) {
    final Package tool = PACKAGES.get(name);
    if (ESLINT_JS.equals(name) && manifest.declares(ESLINT)) {
      return new Package(name, manifest.dependencies().get(ESLINT), tool.node(), tool.typescript());
    }
    return tool;
  }
}
