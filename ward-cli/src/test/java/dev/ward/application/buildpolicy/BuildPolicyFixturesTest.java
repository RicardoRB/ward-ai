package dev.ward.application.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.buildpolicy.BuildRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildPolicyFixturesTest {
  private static final List<String> TOOLS = List.of("maven", "gradle", "node", "python");

  @Test
  void everyViolatingFolderReportsItsExpectedLinesAndEveryPassingFolderIsClean()
      throws IOException {
    final List<String> failures = new ArrayList<>();
    for (final String tool : TOOLS) {
      for (final Path ruleFolder :
          BuildPolicyFixtures.folders(BuildPolicyFixtures.ROOT.resolve(tool))) {
        final BuildRule rule = BuildRule.byName(ruleFolder.getFileName().toString()).orElseThrow();
        final Path violating = ruleFolder.resolve("violating");
        final Path passing = ruleFolder.resolve("passing");
        final List<String> expected = Files.readAllLines(violating.resolve("expected.txt"));
        compare(failures, violating, expected, rule);
        compare(failures, passing, List.of(), rule);
      }
    }
    assertThat(failures).isEmpty();
  }

  private static void compare(
      final List<String> failures,
      final Path folder,
      final List<String> expected,
      final BuildRule rule)
      throws IOException {
    final List<String> actual =
        BuildPolicyFixtures.findings(folder, rule, BuildPolicyFixtures.options(folder));
    if (!actual.equals(expected)) {
      failures.add(folder + ": expected " + expected + " but found " + actual);
    }
  }

  @Test
  void everyRuleThatReadsAFileHasBothFoldersForEachBuildToolItSupports() {
    final List<String> missing = new ArrayList<>();
    final java.util.Map<BuildRule, List<String>> supported =
        java.util.Map.of(
            BuildRule.PLUGIN_VERSION_PINNED,
            List.of("maven", "gradle"),
            BuildRule.NO_DYNAMIC_VERSION,
            TOOLS,
            BuildRule.MINIMUM_BUILD_TOOL_VERSION,
            TOOLS,
            BuildRule.DUPLICATE_DEPENDENCY_DECLARATION,
            List.of("maven"));
    supported.forEach(
        (rule, tools) ->
            tools.forEach(
                tool -> {
                  for (final String kind : List.of("violating", "passing")) {
                    final Path folder =
                        BuildPolicyFixtures.ROOT
                            .resolve(tool)
                            .resolve(rule.ruleName())
                            .resolve(kind);
                    if (!Files.isDirectory(folder)) {
                      missing.add(folder.toString());
                    }
                  }
                }));
    assertThat(missing).isEmpty();
  }
}
