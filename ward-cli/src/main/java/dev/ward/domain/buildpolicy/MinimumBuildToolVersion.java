package dev.ward.domain.buildpolicy;

import java.util.List;
import java.util.Map;

/** {@code ward:build/minimum-build-tool-version}: a declared tool version is not too old. */
final class MinimumBuildToolVersion implements BuildPolicyRule {
  private static final Map<String, String> DEFAULTS = Map.of("maven", "3.9");

  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    return input.model().tools().stream()
        .filter(tool -> minimum(input, tool.tool()) != null)
        .filter(tool -> Versions.compare(tool.version(), minimum(input, tool.tool())) < 0)
        .map(
            tool ->
                new BuildPolicyFinding(
                    BuildRule.MINIMUM_BUILD_TOOL_VERSION,
                    tool.tool()
                        + " "
                        + tool.version()
                        + " is declared, which is below the minimum "
                        + minimum(input, tool.tool())
                        + ".",
                    tool.file(),
                    tool.line()))
        .toList();
  }

  private static String minimum(final BuildPolicyInput input, final String tool) {
    return input.options().getOrDefault(tool, DEFAULTS.get(tool));
  }
}
