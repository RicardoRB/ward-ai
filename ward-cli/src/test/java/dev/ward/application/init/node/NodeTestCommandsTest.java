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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The test runner commands and the coverage check: no threshold flags, only reporters. */
class NodeTestCommandsTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;

  @TempDir Path root;

  @Test
  void wardOwnedConfigsNeedNoReporterFlag() {
    final PackageManifest jest = new PackageManifest(Map.of("jest", "^30.0.0"), null, null);

    for (final QualityPreset preset : new QualityPreset[] {null, QualityPreset.STRICT}) {
      assertThat(gate(BuildTool.NPM, NO_RUNNER, "vitest", preset, false).command())
          .containsExactly("npx", "vitest", "run", "--coverage");
      assertThat(gate(BuildTool.NPM, jest, "jest", preset, false).command())
          .containsExactly("npx", "jest", "--coverage");
    }
  }

  @Test
  void bunAlwaysWritesLcovBesideItsTextReport() {
    assertThat(gate(BuildTool.BUN, NO_RUNNER, "bun-test", QualityPreset.STRICT, true).command())
        .containsExactly(
            "bun", "test", "--coverage", "--coverage-reporter=text", "--coverage-reporter=lcov");
  }

  @ParameterizedTest
  @EnumSource(
      value = QualityPreset.class,
      names = {"STANDARD", "STRICT"})
  void ownVitestConfigGetsReporterFlagsAndNoThreshold(final QualityPreset preset) {
    assertThat(gate(BuildTool.NPM, NO_RUNNER, "vitest", preset, true).command())
        .containsExactly(
            "npx",
            "vitest",
            "run",
            "--coverage",
            "--coverage.reporter=text",
            "--coverage.reporter=lcov");
  }

  @ParameterizedTest
  @EnumSource(
      value = QualityPreset.class,
      names = {"STANDARD", "STRICT"})
  void ownJestConfigGetsReporterFlagsAndNoThreshold(final QualityPreset preset) {
    final PackageManifest jest = new PackageManifest(Map.of("jest", "^30.0.0"), null, null);

    assertThat(gate(BuildTool.NPM, jest, "jest", preset, true).command())
        .containsExactly(
            "npx", "jest", "--coverage", "--coverageReporters=text", "--coverageReporters=lcov");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"        | 70 |    ", "STANDARD| 70 |    ", "STRICT  | 85 | 75"})
  void coverageCheckHoldsThePresetMinimumsAndNoCommand(
      final QualityPreset preset, final String lines, final String branches) {
    final InitGateOption coverage = gate(BuildTool.NPM, NO_RUNNER, "coverage", preset, false);

    assertThat(coverage.command()).isEmpty();
    assertThat(coverage.settings()).containsEntry("minimum", lines);
    if (branches == null) {
      assertThat(coverage.settings()).doesNotContainKey("minimum-branches");
    } else {
      assertThat(coverage.settings()).containsEntry("minimum-branches", branches);
    }
  }

  @Test
  void bunMeasuresNoBranchesSoItsCoverageCheckHasNoBranchMinimum() {
    final InitGateOption coverage =
        gate(BuildTool.BUN, NO_RUNNER, "coverage", QualityPreset.STRICT, false);

    assertThat(coverage.settings()).containsOnlyKeys("minimum").containsEntry("minimum", "85");
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
            root,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    return catalog.gates(project, preset).stream()
        .filter(gate -> gate.id().equals(id))
        .findFirst()
        .orElseThrow();
  }
}
