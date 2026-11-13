package dev.ward.domain.buildpolicy;

/** Creates the implementation of a build rule. */
public final class BuildPolicyRules {
  private BuildPolicyRules() {}

  /** Returns the implementation of {@code rule}. */
  public static BuildPolicyRule forRule(final BuildRule rule) {
    return switch (rule) {
      case PLUGIN_VERSION_PINNED -> new PluginVersionPinned();
      case NO_DYNAMIC_VERSION -> new NoDynamicVersion();
      case MINIMUM_BUILD_TOOL_VERSION -> new MinimumBuildToolVersion();
      case DUPLICATE_DEPENDENCY_DECLARATION -> new DuplicateDependencyDeclaration();
      case REQUIRE_UPPER_BOUND -> new RequireUpperBound();
      default -> new DependencyConvergence();
    };
  }
}
