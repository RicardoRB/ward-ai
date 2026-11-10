package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.node.PackageManifest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The dev dependencies init adds for the Node gates, pinned to the newest releases at least 14 days
 * old on 2026-10-24, with the Node and TypeScript versions each supports.
 */
public final class NodePackageCatalog {
  static final String TYPESCRIPT = "typescript";
  static final String TURBO = "turbo";
  // The newest release at least 14 days old on 2026-10-30. npm publishes no engines range for it.
  public static final String TURBO_VERSION = "2.11.9";
  private static final String STRYKER = "stryker";
  private static final String STRYKER_CORE = "@stryker-mutator/core";
  private static final String STRYKER_VERSION = "10.1.1";
  private static final String STRYKER_NODE = ">=22.0.0";
  private static final String VITEST_NODE = "^20.0.0 || ^22.0.0 || >=24.0.0";
  static final String VITEST_VERSION = "4.0.18";

  /**
   * The framework checkers' supported ranges: the major each was verified in, so a declared checker
   * outside it is {@code INCOMPATIBLE}.
   */
  static final Map<String, String> CHECKER_SUPPORTED =
      Map.of(
          "vue-tsc", ">=3.3.12 <4.0.0",
          "svelte-check", ">=4.7.6 <5.0.0",
          "@astrojs/check", ">=0.9.10 <1.0.0");

  static final String ANGULAR_CORE = "@angular/core";
  static final String NG_TEST = "ng-test";
  static final String VITEST = "vitest";
  static final String VITE = "vite";
  static final String COVERAGE_V8 = "@vitest/coverage-v8";

  /** Stryker's runner plugin for each test gate; Bun's command runner is built into the core. */
  private static final Map<String, String> STRYKER_RUNNERS =
      Map.of("vitest", "@stryker-mutator/vitest-runner", "jest", "@stryker-mutator/jest-runner");

  private static final Map<String, Package> PACKAGES =
      Map.ofEntries(
          entry(new Package(TURBO, TURBO_VERSION, ">=18", null)),
          entry(new Package("vitest", VITEST_VERSION, VITEST_NODE, null)),
          entry(new Package(COVERAGE_V8, VITEST_VERSION, VITEST_NODE, null)),
          // The TypeScript compiler's pin; the framework checkers name their own ranges.
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
          entry(new Package(STRYKER_RUNNERS.get("jest"), STRYKER_VERSION, STRYKER_NODE, null)));

  private static final Map<String, List<String>> GATES =
      Map.ofEntries(
          Map.entry("tsc", List.of(TYPESCRIPT)),
          Map.entry("vitest", List.of("vitest", COVERAGE_V8)),
          Map.entry(NG_TEST, List.of(COVERAGE_V8)),
          Map.entry("jest", List.of()),
          Map.entry("bun-test", List.of()),
          Map.entry(STRYKER, List.of(STRYKER_CORE)),
          Map.entry("vue-tsc", List.of("vue-tsc", TYPESCRIPT)),
          Map.entry("svelte-check", List.of("svelte-check", TYPESCRIPT)),
          Map.entry("astro-check", List.of("@astrojs/check", TYPESCRIPT)));

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

  /** Returns the packages {@code gate} needs, empty for a gate that is not a Node tool. */
  static List<String> packages(final String gate) {
    return GATES.getOrDefault(gate, List.of());
  }

  static Optional<String> angularMajor(final PackageManifest manifest) {
    if (!manifest.declares(ANGULAR_CORE)) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(manifest.dependencies().get(ANGULAR_CORE))
        .map(version -> version.substring(0, version.indexOf('.')));
  }

  /**
   * Returns the packages {@code gates} need that {@code manifest} does not declare, each once, in
   * gate order.
   */
  static List<Package> missing(final List<InitGateOption> gates, final PackageManifest manifest) {
    return missing(gates, manifest, Optional.empty());
  }

  /**
   * Returns the packages {@link #missing(List, PackageManifest)} returns, plus the task runner's
   * package when {@code runnerPackage} is present: the root of a workspace runs its tasks with it.
   */
  static List<Package> missing(
      final List<InitGateOption> gates,
      final PackageManifest manifest,
      final Optional<String> runnerPackage) {
    final Set<String> names = new LinkedHashSet<>();
    runnerPackage.ifPresent(names::add);
    gates.forEach(gate -> names.addAll(packages(gate.id())));
    if (names.contains(STRYKER_CORE)) {
      gates.stream()
          .map(gate -> STRYKER_RUNNERS.get(gate.id()))
          .filter(Objects::nonNull)
          .forEach(names::add);
    }
    return names.stream()
        .filter(name -> !manifest.declares(name))
        .flatMap(name -> pinned(name, manifest).stream())
        .toList();
  }

  /** Returns the catalog pin of {@code name}, with Vitest and its coverage following the Vite. */
  private static Optional<Package> pinned(final String name, final PackageManifest manifest) {
    final Package tool = PACKAGES.get(name);
    if (COVERAGE_V8.equals(name) && manifest.declares(VITEST)) {
      // Each coverage release requires its own Vitest version, so it follows the declared one.
      return Optional.of(new Package(name, manifest.dependencies().get(VITEST), tool.node(), null));
    }
    if (VITEST.equals(name) || COVERAGE_V8.equals(name)) {
      return Optional.of(new Package(name, pinnedVitest(manifest), tool.node(), null));
    }
    return Optional.of(tool);
  }

  /** Returns the Vitest release for the project's Vite, or the default pin without a Vite. */
  private static String pinnedVitest(final PackageManifest manifest) {
    final Optional<String> vite =
        manifest.declares(VITE)
            ? NodeVersionRange.lowerBound(manifest.dependencies().get(VITE))
            : Optional.empty();
    return vite.flatMap(NodeVitestReleases::forVite)
        .map(NodeVitestReleases.Release::version)
        .orElse(VITEST_VERSION);
  }
}
