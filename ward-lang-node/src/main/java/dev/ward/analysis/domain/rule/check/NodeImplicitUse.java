package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * The packages a workspace uses without importing them: the tool its config files configure, the
 * package a script runs, a package a used package requires as a peer, and the vitest coverage
 * provider its config selects. Each row names the knip plugin it stands for. A package used this
 * way is not reported as unused, so a fact this table does not know produces no finding.
 */
final class NodeImplicitUse {

  /** Config file names and the package each configures; knip's plugin of that tool reads them. */
  private static final Map<String, String> CONFIG_PACKAGES =
      Map.ofEntries(
          // knip vitest plugin
          Map.entry("vitest.config.ts", "vitest"),
          Map.entry("vitest.config.mts", "vitest"),
          Map.entry("vitest.config.js", "vitest"),
          Map.entry("vitest.config.mjs", "vitest"),
          // knip vite plugin
          Map.entry("vite.config.ts", "vite"),
          Map.entry("vite.config.mts", "vite"),
          Map.entry("vite.config.js", "vite"),
          Map.entry("vite.config.mjs", "vite"),
          // knip jest plugin
          Map.entry("jest.config.ts", "jest"),
          Map.entry("jest.config.js", "jest"),
          Map.entry("jest.config.mjs", "jest"),
          Map.entry("jest.config.cjs", "jest"),
          // knip playwright plugin
          Map.entry("playwright.config.ts", "@playwright/test"),
          Map.entry("playwright.config.js", "@playwright/test"),
          Map.entry("playwright.config.mjs", "@playwright/test"),
          // knip eslint plugin
          Map.entry("eslint.config.js", "eslint"),
          Map.entry("eslint.config.mjs", "eslint"),
          Map.entry("eslint.config.cjs", "eslint"),
          Map.entry("eslint.config.ts", "eslint"),
          Map.entry(".eslintrc.json", "eslint"),
          Map.entry(".eslintrc.js", "eslint"),
          Map.entry(".eslintrc.cjs", "eslint"),
          // knip prettier plugin
          Map.entry(".prettierrc", "prettier"),
          Map.entry(".prettierrc.json", "prettier"),
          Map.entry("prettier.config.js", "prettier"),
          Map.entry("prettier.config.mjs", "prettier"),
          // knip babel plugin
          Map.entry("babel.config.js", "@babel/core"),
          Map.entry("babel.config.json", "@babel/core"),
          Map.entry(".babelrc", "@babel/core"),
          // knip postcss plugin
          Map.entry("postcss.config.js", "postcss"),
          Map.entry("postcss.config.mjs", "postcss"),
          Map.entry("postcss.config.cjs", "postcss"),
          // knip stryker plugin
          Map.entry("stryker.config.js", "@stryker-mutator/core"),
          Map.entry("stryker.config.mjs", "@stryker-mutator/core"),
          Map.entry("stryker.config.cjs", "@stryker-mutator/core"),
          Map.entry("stryker.config.ts", "@stryker-mutator/core"),
          // knip tailwind plugin
          Map.entry("tailwind.config.js", "tailwindcss"),
          Map.entry("tailwind.config.ts", "tailwindcss"),
          Map.entry("tailwind.config.mjs", "tailwindcss"));

  /** The vitest coverage provider named in a vitest config, and the package that implements it. */
  private static final Map<String, String> VITEST_PROVIDERS =
      Map.of("v8", "@vitest/coverage-v8", "istanbul", "@vitest/coverage-istanbul");

  /** Stryker test runners named in a stryker config, by the runner name that selects them. */
  private static final Set<String> STRYKER_RUNNERS =
      Set.of("vitest", "jest", "mocha", "karma", "jasmine", "cucumber");

  /** Jest transforms and environments that a jest config names by their package. */
  private static final Set<String> JEST_PACKAGES =
      Set.of("ts-jest", "babel-jest", "jest-environment-jsdom", "@swc/jest");

  /** Commands that a package installs, for a script run when node_modules is not installed. */
  private static final Map<String, String> KNOWN_BINARIES =
      Map.of(
          "tsc", "typescript",
          "playwright", "@playwright/test",
          "babel", "@babel/cli",
          "stryker", "@stryker-mutator/core");

  private static final String VITEST_CONFIG = "vitest.config.";
  private static final String STRYKER_CONFIG = "stryker.config.";
  private static final String JEST_CONFIG = "jest.config.";

  private NodeImplicitUse() {}

  /**
   * The packages that a workspace uses without an import, given the project's files and the
   * packages installed under node_modules (empty when none are installed).
   */
  static Set<String> of(
      final NodeManifests.Manifest owner,
      final SortedMap<String, SyntaxNode> trees,
      final ProjectFiles files,
      final Map<String, NodeManifests.Manifest> installed) {
    final Set<String> implied = new HashSet<>();
    configs(owner.directory(), trees, files, implied);
    scripts(owner.manifest(), installed, implied);
    return implied;
  }

  /** The tool each config file in the workspace's directory configures. */
  private static void configs(
      final String directory,
      final SortedMap<String, SyntaxNode> trees,
      final ProjectFiles files,
      final Set<String> implied) {
    for (final String path : trees.keySet()) {
      addConfig(directory, path, trees.get(path), implied);
    }
    for (final String path : files.paths()) {
      addConfig(directory, path, null, implied);
    }
  }

  /**
   * Adds the package a config file names, and for a vitest config the coverage provider it selects.
   */
  private static void addConfig(
      final String directory, final String path, final SyntaxNode tree, final Set<String> implied) {
    if (!NodeManifests.directoryOf(path).equals(directory)) {
      return;
    }
    final String name = path.substring(path.lastIndexOf('/') + 1);
    if ("tsconfig.json".equals(name) || name.startsWith("tsconfig.") && name.endsWith(".json")) {
      implied.add("typescript");
    }
    Optional.ofNullable(CONFIG_PACKAGES.get(name)).ifPresent(implied::add);
    if (tree != null) {
      addLiterals(name, NodeFileFacts.stringLiterals(tree), implied);
    }
  }

  /**
   * The packages that a config's string literals name: a vitest coverage provider, a stryker runner
   * or a jest package.
   */
  private static void addLiterals(
      final String name, final List<String> literals, final Set<String> implied) {
    for (final String literal : literals) {
      if (name.startsWith(VITEST_CONFIG)) {
        Optional.ofNullable(VITEST_PROVIDERS.get(literal)).ifPresent(implied::add);
      } else if (name.startsWith(STRYKER_CONFIG) && STRYKER_RUNNERS.contains(literal)) {
        implied.add("@stryker-mutator/" + literal + "-runner");
      } else if (name.startsWith(JEST_CONFIG) && JEST_PACKAGES.contains(literal)) {
        implied.add(literal);
      }
    }
  }

  /**
   * The packages that a script runs, by the command name it types, from node_modules or the known
   * list.
   */
  private static void scripts(
      final NodeManifest manifest,
      final Map<String, NodeManifests.Manifest> installed,
      final Set<String> implied) {
    final Map<String, String> providers = binaryProviders(installed);
    for (final String script : NodeManifests.texts(manifest, "scripts")) {
      for (final String token : script.trim().split("\\s+")) {
        Optional.ofNullable(providers.getOrDefault(token, KNOWN_BINARIES.get(token)))
            .ifPresent(implied::add);
      }
    }
  }

  /** The package that installs each command, from the installed packages' bin fields. */
  private static Map<String, String> binaryProviders(
      final Map<String, NodeManifests.Manifest> installed) {
    final Map<String, String> providers = new HashMap<>();
    installed.forEach(
        (name, owner) ->
            NodeInstalledPackages.binaryNames(name, owner.manifest())
                .forEach(command -> providers.put(command, name)));
    return providers;
  }

  /** The peer requirements of the packages in {@code used}, when those packages are installed. */
  static Set<String> peersOf(
      final Set<String> used, final Map<String, NodeManifests.Manifest> installed) {
    final Set<String> peers = new HashSet<>();
    for (final String name : used) {
      Optional.ofNullable(installed.get(name))
          .ifPresent(
              installedPackage ->
                  peers.addAll(
                      NodeManifests.names(
                          installedPackage.manifest(), List.of("peerDependencies"))));
    }
    return peers;
  }
}
