package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
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

  /**
   * The framework checkers' supported ranges: the major each was verified in, so a declared checker
   * outside it is {@code INCOMPATIBLE}.
   */
  static final Map<String, String> CHECKER_SUPPORTED =
      Map.of(
          "vue-tsc", ">=3.3.12 <4.0.0",
          "svelte-check", ">=4.7.6 <5.0.0",
          "@astrojs/check", ">=0.9.10 <1.0.0");

  /** The ESLint releases with {@code eslint/config}, which the generated config imports. */
  static final String ESLINT_SUPPORTED = ">=9.22.0 <11.0.0";

  static final String ANGULAR_CORE = "@angular/core";
  static final String ANGULAR_ESLINT = "angular-eslint";
  static final String NG_TEST = "ng-test";
  static final String VITEST = "vitest";
  static final String COVERAGE_V8 = "@vitest/coverage-v8";

  /**
   * angular-eslint for each Angular major the matrix verified. The Node range is the Angular CLI's,
   * since angular-eslint declares none of its own.
   */
  private static final Map<String, Package> ANGULAR_ESLINT_BY_MAJOR =
      Map.of(
          "21",
          new Package(ANGULAR_ESLINT, "21.4.0", "^20.19.0 || ^22.12.0 || >=24.0.0", null),
          "22",
          new Package(ANGULAR_ESLINT, "22.6.0", "^22.22.3 || ^24.15.0 || >=26.0.0", null));

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
          // Verified on Node 22.12 and 24 with TypeScript 5.9.3 and 6.0.3; Node 18 for
          // svelte-check.
          entry(new Package("vue-tsc", "3.3.12", ">=22.12.0", ">=5.9.3")),
          entry(new Package("svelte-check", "4.7.6", ">=18.0.0", "^5.0.0 || ^6.0.0")),
          entry(new Package("@astrojs/check", "0.9.10", ">=22.12.0", "^5.0.0 || ^6.0.0")),
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
      Map.ofEntries(
          Map.entry("tsc", List.of(TYPESCRIPT)),
          Map.entry(ESLINT, List.of(ESLINT, ESLINT_JS, TYPESCRIPT_ESLINT, TYPESCRIPT)),
          Map.entry("prettier", List.of("prettier")),
          Map.entry("vitest", List.of("vitest", "@vitest/coverage-v8")),
          Map.entry(NG_TEST, List.of(COVERAGE_V8)),
          Map.entry("jest", List.of()),
          Map.entry("bun-test", List.of()),
          Map.entry(STRYKER, List.of(STRYKER_CORE)),
          Map.entry(DEPENDENCY_CRUISER, List.of(DEPENDENCY_CRUISER, TYPESCRIPT)),
          Map.entry(BIOME, List.of(BIOME_PACKAGE)),
          Map.entry("vue-tsc", List.of("vue-tsc", TYPESCRIPT)),
          Map.entry("svelte-check", List.of("svelte-check", TYPESCRIPT)),
          Map.entry("astro-check", List.of("@astrojs/check", TYPESCRIPT)));

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
   * Returns the angular-eslint release pinned for the project's Angular major, or empty when the
   * manifest does not declare Angular or the major has no pin.
   */
  static Optional<Package> angularEslint(final PackageManifest manifest) {
    return angularMajor(manifest).map(ANGULAR_ESLINT_BY_MAJOR::get);
  }

  private static Optional<String> angularMajor(final PackageManifest manifest) {
    if (!manifest.declares(ANGULAR_CORE)) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(manifest.dependencies().get(ANGULAR_CORE))
        .map(version -> version.substring(0, version.indexOf('.')));
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
    if (gates.stream().anyMatch(gate -> NG_TEST.equals(gate.id()))
        && gates.stream().anyMatch(gate -> ESLINT.equals(gate.id()))) {
      names.add(ANGULAR_ESLINT);
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
    if (ANGULAR_ESLINT.equals(name)) {
      return angularEslint(manifest)
          .orElseThrow(
              () ->
                  new WardException("No angular-eslint release is pinned for this Angular major."));
    }
    if (COVERAGE_V8.equals(name) && manifest.declares(ANGULAR_CORE) && manifest.declares(VITEST)) {
      // Angular's unit-test builder needs the coverage package at the Vitest version it runs.
      return new Package(name, manifest.dependencies().get(VITEST), tool.node(), null);
    }
    return tool;
  }
}
