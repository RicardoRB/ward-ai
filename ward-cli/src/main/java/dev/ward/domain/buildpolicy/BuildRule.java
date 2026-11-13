package dev.ward.domain.buildpolicy;

import java.util.List;
import java.util.Optional;

/** The build policy rules: one row per rule with its id, preset membership and options. */
public enum BuildRule {
  PLUGIN_VERSION_PINNED("plugin-version-pinned", true, List.of()),
  NO_DYNAMIC_VERSION("no-dynamic-version", true, List.of()),
  MINIMUM_BUILD_TOOL_VERSION(
      "minimum-build-tool-version",
      true,
      List.of("maven", "gradle", "npm", "pnpm", "yarn", "bun", "uv")),
  DUPLICATE_DEPENDENCY_DECLARATION("duplicate-dependency-declaration", true, List.of()),
  DEPENDENCY_CONVERGENCE("dependency-convergence", false, List.of());

  /** The prefix of every build rule id. */
  public static final String PREFIX = "ward:build/";

  private final String ruleName;
  private final boolean strict;
  private final List<String> options;

  BuildRule(final String ruleName, final boolean strict, final List<String> options) {
    this.ruleName = ruleName;
    this.strict = strict;
    this.options = options;
  }

  /** Returns the rule name, such as {@code no-dynamic-version}. */
  public String ruleName() {
    return ruleName;
  }

  /** Returns the rule id, such as {@code ward:build/no-dynamic-version}. */
  public String id() {
    return PREFIX + ruleName;
  }

  /** Returns whether the {@code strict} preset selects the rule. */
  public boolean strict() {
    return strict;
  }

  /** Returns the option names of the rule; each is a string. */
  public List<String> options() {
    return List.copyOf(options);
  }

  /** Returns the rule with {@code name}, as written in {@code [select]} (without prefix). */
  public static Optional<BuildRule> byName(final String name) {
    return List.of(values()).stream().filter(rule -> rule.ruleName.equals(name)).findFirst();
  }
}
