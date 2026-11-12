package dev.ward.domain.buildpolicy;

import java.util.List;

/** {@code ward:build/plugin-version-pinned}: every plugin has an exact version. */
final class PluginVersionPinned implements BuildPolicyRule {
  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    return input.model().declarations().stream()
        .filter(declaration -> declaration.kind() == DeclarationKind.PLUGIN)
        .filter(declaration -> !declaration.flag().equals(Declaration.Flag.LOCKED))
        .map(PluginVersionPinned::violation)
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  private static java.util.Optional<BuildPolicyFinding> violation(final Declaration plugin) {
    final String problem = problem(plugin);
    if (problem.isEmpty()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        new BuildPolicyFinding(
            BuildRule.PLUGIN_VERSION_PINNED,
            "Plugin " + plugin.name() + " " + problem,
            plugin.file(),
            plugin.line()));
  }

  private static String problem(final Declaration plugin) {
    if (plugin.flag() == Declaration.Flag.UNDETERMINABLE) {
      return "has a version that cannot be determined from the file.";
    }
    if (!plugin.versioned()) {
      return plugin.flag() == Declaration.Flag.EXTERNAL_PARENT
          ? "has no version here; it may be inherited from the external parent POM, which Ward does"
              + " not read. Pin it in pluginManagement."
          : "has no version.";
    }
    return Versions.exact(plugin.ecosystem(), plugin.version())
        ? ""
        : "has the version " + plugin.version() + ", which is not an exact version.";
  }
}
