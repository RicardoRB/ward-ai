package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
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
import org.junit.jupiter.api.io.TempDir;

/** The reporter flags the test gate carries when crap is selected, and when it carries none. */
class NodeCrapCommandsTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;
  private static final PackageManifest JEST =
      new PackageManifest(Map.of("jest", "^30.0.0"), null, null);

  @TempDir Path root;

  @Test
  void wardOwnedVitestCommandKeepsItsFlagsBecauseTheConfigListsTheReporters() {
    assertThat(command(BuildTool.NPM, NO_RUNNER, "vitest", false, true))
        .containsExactly("npx", "vitest", "run", "--coverage");
  }

  @Test
  void ownedVitestConfigGetsBothReporterFlags() {
    assertThat(command(BuildTool.NPM, NO_RUNNER, "vitest", true, true))
        .endsWith("--coverage.reporter=text", "--coverage.reporter=lcov");
  }

  @Test
  void wardOwnedJestCommandCarriesNoReporterFlag() {
    assertThat(command(BuildTool.NPM, JEST, "jest", false, true))
        .containsExactly("npx", "jest", "--coverage");
  }

  @Test
  void ownedJestConfigGetsBothReporterFlags() {
    assertThat(command(BuildTool.NPM, JEST, "jest", true, true))
        .endsWith("--coverageReporters=text", "--coverageReporters=lcov");
  }

  @Test
  void bunAlwaysCarriesTheReporterFlags() {
    assertThat(command(BuildTool.BUN, NO_RUNNER, "bun-test", false, true))
        .containsExactly(
            "bun", "test", "--coverage", "--coverage-reporter=text", "--coverage-reporter=lcov");
  }

  @Test
  void withoutCrapTheCommandsAreUnchanged() {
    assertThat(command(BuildTool.BUN, NO_RUNNER, "bun-test", false, false))
        .containsExactly("bun", "test", "--coverage");
    assertThat(command(BuildTool.NPM, NO_RUNNER, "vitest", true, false))
        .noneMatch(argument -> argument.contains("reporter"));
  }

  @Test
  void angularIsNotOffered() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(inputs(NO_RUNNER, false));

    assertThat(catalog.gates(project(BuildTool.NPM, Framework.ANGULAR, TaskRunner.NONE), null))
        .extracting(InitGateOption::id)
        .contains("ng-test")
        .doesNotContain("crap");
  }

  private List<String> command(
      final BuildTool manager,
      final PackageManifest manifest,
      final String id,
      final boolean ownConfig,
      final boolean crap) {
    final InitInputs inputs = inputs(manifest, ownConfig);
    final Project project = project(manager, Framework.NONE, TaskRunner.NONE);
    final List<InitGateOption> offered =
        new InitGeneralCatalog(inputs)
            .gates(project, QualityPreset.STANDARD).stream()
                .filter(gate -> gate.id().equals(id) || crap && gate.id().equals("crap"))
                .toList();
    return new NodeInitSupport()
        .withCrap(project, offered, inputs).stream()
            .filter(gate -> gate.id().equals(id))
            .findFirst()
            .orElseThrow()
            .command();
  }

  private static InitInputs inputs(final PackageManifest manifest, final boolean own) {
    return new InitInputs(
        new FakeEnvironmentInspection("npm", "bun"),
        directory -> manifest,
        directory -> false,
        (directory, gate) ->
            own ? Optional.of(directory.resolve("vite.config.ts")) : Optional.empty());
  }

  private Project project(
      final BuildTool manager, final Framework framework, final TaskRunner runner) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        manager,
        framework,
        false,
        false,
        root,
        Set.of(),
        "24",
        runner,
        null);
  }
}
