package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The test runner commands, whose coverage flags depend on the preset and the project's config. */
class NodeTestCommandsTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;

  @TempDir Path root;

  @Test
  void wardOwnedTestConfigsKeepThresholdsOutOfTheCommand() {
    final PackageManifest jest = new PackageManifest(Map.of("jest", "^30.0.0"), null, null);

    for (final QualityPreset preset : new QualityPreset[] {null, QualityPreset.STRICT}) {
      assertThat(gate(BuildTool.NPM, NO_RUNNER, "vitest", preset, false).command())
          .containsExactly("npx", "vitest", "run", "--coverage");
      assertThat(gate(BuildTool.NPM, jest, "jest", preset, false).command())
          .containsExactly("npx", "jest", "--coverage");
    }
    assertThat(gate(BuildTool.BUN, NO_RUNNER, "bun-test", QualityPreset.STRICT, true).command())
        .containsExactly("bun", "test", "--coverage");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "        | --coverage.thresholds.lines=70 |                                   ",
        "STANDARD| --coverage.thresholds.lines=70 |                                   ",
        "STRICT  | --coverage.thresholds.lines=85 | --coverage.thresholds.branches=75"
      })
  void ownVitestConfigGetsThePresetThresholdsAsFlags(
      final QualityPreset preset, final String lines, final String branches) {
    final List<String> expected =
        new ArrayList<>(List.of("npx", "vitest", "run", "--coverage", lines));
    if (branches != null) {
      expected.add(branches);
    }

    assertThat(gate(BuildTool.NPM, NO_RUNNER, "vitest", preset, true).command())
        .containsExactlyElementsOf(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "        | '{\"global\":{\"lines\":70}}'",
        "STRICT  | '{\"global\":{\"lines\":85,\"branches\":75}}'"
      })
  void ownJestConfigGetsThePresetThresholdsAsFlags(
      final QualityPreset preset, final String threshold) {
    final PackageManifest jest = new PackageManifest(Map.of("jest", "^30.0.0"), null, null);

    assertThat(gate(BuildTool.NPM, jest, "jest", preset, true).command())
        .containsExactly("npx", "jest", "--coverage", "--coverageThreshold=" + threshold);
  }

  private InitGateOption gate(
      final BuildTool manager,
      final PackageManifest manifest,
      final String id,
      final QualityPreset preset,
      final boolean ownConfig) {
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm", "pnpm"),
                directory -> manifest,
                directory -> false,
                (directory, gate) ->
                    ownConfig
                        ? Optional.of(directory.resolve("vite.config.ts"))
                        : Optional.empty()));
    final Project project =
        new Project(
            root,
            Language.TYPESCRIPT,
            manager,
            Framework.NONE,
            false,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE);
    return catalog.gates(project, preset).stream()
        .filter(gate -> gate.id().equals(id))
        .findFirst()
        .orElseThrow();
  }
}
