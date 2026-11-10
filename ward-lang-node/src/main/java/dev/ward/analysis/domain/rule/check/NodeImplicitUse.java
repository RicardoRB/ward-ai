package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The packages a workspace uses without importing them: the tool its config files configure, the
 * package a script runs, a package a used package requires as a peer, the vitest coverage provider
 * its config selects, and tslib when a tsconfig sets {@code importHelpers}. Each row names the knip
 * plugin it stands for. A package used this way is not reported as unused, so a fact this table
 * does not know produces no finding.
 */
public final class NodeImplicitUse {

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

  private static final String VITEST_CONFIG = "vitest.config.";
  private static final String STRYKER_CONFIG = "stryker.config.";
  private static final String JEST_CONFIG = "jest.config.";
  private static final String TSCONFIG = "tsconfig.json";
  private static final String ANGULAR_CONFIG = "angular.json";
  private static final String WARD_CONFIG = "ward.toml";

  /**
   * A glob for every tsconfig variant, such as {@code tsconfig.app.json}, that a manifest read
   * names.
   */
  public static final String TSCONFIG_VARIANTS = "tsconfig.*.json";

  private static final String TSCONFIG_PREFIX = "tsconfig.";
  private static final String JSON_SUFFIX = ".json";
  private static final Pattern QUOTED = Pattern.compile("[\"']([^\"'\\s]+)[\"']");

  private NodeImplicitUse() {}

  /**
   * The config file names this table reads, so a project carries them whatever their language: init
   * writes a {@code .mjs} vitest config even for a TypeScript project.
   */
  public static Set<String> configFileNames() {
    final Set<String> names = new HashSet<>(CONFIG_PACKAGES.keySet());
    names.add(TSCONFIG);
    names.add(TSCONFIG_VARIANTS);
    names.add(ANGULAR_CONFIG);
    names.add(WARD_CONFIG);
    return Set.copyOf(names);
  }

  /** Tells whether a file name is one of the config files, a tsconfig variant included. */
  public static boolean isConfigFile(final String name) {
    return configFileNames().contains(name) || isTsconfigVariant(name);
  }

  private static boolean isTsconfigVariant(final String name) {
    return name.startsWith(TSCONFIG_PREFIX) && name.endsWith(JSON_SUFFIX);
  }

  /**
   * The packages that a workspace uses without an import, given the project's files and the
   * packages installed under node_modules (empty when none are installed).
   */
  static Set<String> of(
      final NodeManifests.Manifest owner,
      final ProjectFiles files,
      final Map<String, NodeManifests.Manifest> installed) {
    final Set<String> implied = new HashSet<>();
    configs(owner.directory(), files, implied);
    scripts(owner.manifest(), installed, implied);
    gateCommands(owner.directory(), files, installed, implied);
    return implied;
  }

  /** The tool each config file in the workspace's directory configures, read from its text. */
  private static void configs(
      final String directory, final ProjectFiles files, final Set<String> implied) {
    for (final String path : files.paths()) {
      final String name = path.substring(path.lastIndexOf('/') + 1);
      if (NodeManifests.directoryOf(path).equals(directory) && isConfigFile(name)) {
        addConfig(name, files.text(path).orElse(""), implied);
      }
    }
  }

  /** Adds the package a config file names, and the packages its quoted values name. */
  private static void addConfig(final String name, final String text, final Set<String> implied) {
    if (TSCONFIG.equals(name) || isTsconfigVariant(name)) {
      NodeConfigText.addTsconfig(text, implied);
    }
    if (ANGULAR_CONFIG.equals(name)) {
      NodeConfigText.addAngular(text, implied);
    }
    Optional.ofNullable(CONFIG_PACKAGES.get(name)).ifPresent(implied::add);
    addLiterals(
        name, QUOTED.matcher(text).results().map(match -> match.group(1)).toList(), implied);
  }

  /**
   * The packages that a config's quoted values name: a vitest coverage provider, a stryker runner
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
    final Map<String, String> providers = NodeCommands.binaryProviders(installed);
    NodeManifests.texts(manifest, "scripts")
        .forEach(script -> implied.addAll(NodeCommands.packagesOf(script, providers)));
  }

  /**
   * The packages that the root {@code ward.toml} gate commands run. Ward configured them itself, so
   * knip cannot see them, but the packages are used all the same. Only the root config covers the
   * workspace at the root, so other folders read none.
   */
  private static void gateCommands(
      final String directory,
      final ProjectFiles files,
      final Map<String, NodeManifests.Manifest> installed,
      final Set<String> implied) {
    if (!directory.isEmpty()) {
      return;
    }
    final Map<String, String> providers = NodeCommands.binaryProviders(installed);
    files
        .text(WARD_CONFIG)
        .ifPresent(
            text ->
                NodeConfigText.gateCommands(text)
                    .forEach(
                        command -> implied.addAll(NodeCommands.packagesOf(command, providers))));
  }

  /**
   * The peer requirements of the packages in {@code used}, when those packages are installed, and
   * the peers of those peers: a peer that is installed is used as far as it names peers in turn.
   */
  static Set<String> peersOf(
      final Set<String> used, final Map<String, NodeManifests.Manifest> installed) {
    final Set<String> peers = new HashSet<>();
    final Deque<String> pending = new ArrayDeque<>(used);
    final Set<String> visited = new HashSet<>(used);
    while (!pending.isEmpty()) {
      final String name = pending.pop();
      for (final String peer : peerNames(installed.get(name))) {
        peers.add(peer);
        if (visited.add(peer)) {
          pending.push(peer);
        }
      }
    }
    return peers;
  }

  private static Set<String> peerNames(final NodeManifests.Manifest installedPackage) {
    return installedPackage == null
        ? Set.of()
        : NodeManifests.names(installedPackage.manifest(), List.of("peerDependencies"));
  }
}
