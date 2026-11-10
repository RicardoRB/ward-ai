package dev.ward.analysis.domain.rule.check;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The packages that a command line uses: the package that installs each command a script or gate
 * runs, the known binaries of packages that may not be installed, and what a gate command implies.
 * Scripts and gate commands are read the same way.
 */
final class NodeCommands {

  /** Commands that a package installs, for a script run when node_modules is not installed. */
  private static final Map<String, String> KNOWN_BINARIES =
      Map.of(
          "tsc", "typescript",
          "playwright", "@playwright/test",
          "babel", "@babel/cli",
          "stryker", "@stryker-mutator/core");

  private NodeCommands() {}

  /** The package that installs each command, from the installed packages' bin fields. */
  static Map<String, String> binaryProviders(final Map<String, NodeManifests.Manifest> installed) {
    final Map<String, String> providers = new HashMap<>();
    installed.forEach(
        (name, owner) ->
            NodeInstalledPackages.binaryNames(name, owner.manifest())
                .forEach(command -> providers.put(command, name)));
    return providers;
  }

  /**
   * The packages a command line names: each token that is an installed command's package or a known
   * binary, and the packages a gate command implies.
   */
  static Set<String> packagesOf(final String command, final Map<String, String> providers) {
    final List<String> tokens = List.of(command.trim().split("\\s+"));
    final Set<String> packages = new HashSet<>(NodeConfigText.impliedBy(tokens));
    tokens.forEach(
        token ->
            Optional.ofNullable(providers.getOrDefault(token, KNOWN_BINARIES.get(token)))
                .ifPresent(packages::add));
    return packages;
  }
}
