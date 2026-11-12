package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.AgentRulesTemplate;
import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.NodeInputs;
import dev.ward.application.init.OwnedSections;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.node.PackageManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The CI workflow, agent rules and .gitignore section init writes for a TypeScript project. */
class NodeInitFilesTest {
  private static final Path ROOT = Path.of("/work/shop").toAbsolutePath();

  @Test
  void npmWorkflowSetsUpTheDetectedNodeAndInstallsFromTheLockFile() {
    final String body = workflow(BuildTool.NPM, "22", null);

    assertThat(body)
        .contains(
            "uses: actions/setup-node@v5", "node-version: \"22\"", "cache: npm", "run: npm ci")
        .doesNotContain("setup-java", "pnpm/action-setup");
  }

  @Test
  void pnpmWorkflowSetsUpPnpmBeforeNode() {
    final String body = workflow(BuildTool.PNPM, "24", null);

    assertThat(body.indexOf("pnpm/action-setup@v4")).isLessThan(body.indexOf("actions/setup-node"));
    assertThat(body).contains("cache: pnpm", "run: pnpm install --frozen-lockfile");
  }

  @ParameterizedTest
  @CsvSource({
    "yarn@4.5.0, yarn install --immutable",
    "yarn@1.22.22, yarn install --frozen-lockfile"
  })
  void yarnInstallDependsOnItsVersion(final String packageManager, final String install) {
    assertThat(workflow(BuildTool.YARN, "24", packageManager)).contains("run: " + install);
  }

  @Test
  void bunWorkflowSetsUpBun() {
    assertThat(workflow(BuildTool.BUN, "24", null))
        .contains("uses: oven-sh/setup-bun@v2", "run: bun install --frozen-lockfile")
        .doesNotContain("setup-node", "setup-java");
  }

  @Test
  void typeScriptAgentRulesNameWardRulesTheNodeToolsAndNoJavaTool() {
    final List<InitGateOption> gates =
        new InitGeneralCatalog(
                InitInputs.withoutManifests(new dev.ward.domain.FakeEnvironmentInspection("npm")))
            .gates(project(BuildTool.NPM, "24"), null).stream()
                .filter(gate -> Set.of("tsc", "vitest", "coverage").contains(gate.id()))
                .toList();

    final String body =
        AgentRulesTemplate.render(
                project(BuildTool.NPM, "24"), gates, QualityPreset.STANDARD, List.of())
            .body();

    assertThat(body)
        .contains(
            "ward-rules.toml",
            "`npx tsc --noEmit --pretty false`",
            "Tests (Vitest)",
            "at least 70% line coverage")
        .doesNotContain("ESLint", "Biome", "Checkstyle", "PMD", "JaCoCo", "SpotBugs");
  }

  @Test
  void nodeProjectGetsAGitignoreSectionForReports() {
    final InitSetupCatalog catalog =
        new InitSetupCatalog(
            new dev.ward.domain.FakeEnvironmentInspection(),
            new NodeInputs(
                directory -> PackageManifest.EMPTY,
                directory -> false,
                (directory, gate) -> Optional.empty()),
            null,
            null,
            "dev");
    final List<OwnedSection> sections =
        OwnedSections.of(
            catalog, project(BuildTool.NPM, "24"), selection(null, false), List.of(), List.of());

    assertThat(sections)
        .singleElement()
        .satisfies(
            section -> {
              assertThat(section.file()).isEqualTo(ROOT.resolve(".gitignore"));
              assertThat(section.body()).isEqualTo(".ward/reports/\n.ward/cache/\n");
              assertThat(section.wholeFile()).isFalse();
            });
  }

  private static String workflow(
      final BuildTool manager, final String node, final String packageManager) {
    return CiWorkflowTemplate.render(
            "github",
            project(manager, node),
            List.of(new InitGateOption("tsc", "tsc", List.of("npx", "tsc"), true, "ok")),
            "0.1.3",
            new PackageManifest(Map.of(), null, packageManager))
        .body();
  }

  private static InitSelection selection(final String ci, final boolean agentRules) {
    return new InitSelection(
        List.of("none"), List.of(), null, null, List.of(), false, ci, agentRules, null);
  }

  private static Project project(final BuildTool manager, final String node) {
    return new Project(
        ROOT,
        Language.TYPESCRIPT,
        manager,
        Framework.NONE,
        false,
        false,
        ROOT,
        Set.of(),
        node,
        TaskRunner.NONE,
        null);
  }
}
