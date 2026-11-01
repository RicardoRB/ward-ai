package dev.ward.application.init.node.turbo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.PartialCheckDefaults;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The lint gate of a Turborepo root narrows to the package an edit is in. */
class TurboLintPartialTest {
  private static final Project TURBO_ROOT =
      new Project(
          Path.of("/work/monorepo"),
          Language.TYPESCRIPT,
          BuildTool.PNPM,
          Framework.NONE,
          false,
          false,
          Path.of("/work/monorepo"),
          Set.of(),
          "24",
          TaskRunner.TURBO,
          null);

  @Test
  void editInSharedPackageNarrowsTheTurboLintToItsFilter() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("eslint", TURBO_ROOT, QualityPreset.STRICT, List.of())
            .orElseThrow();

    assertThat(table.args()).containsExactly("{paths}");
    assertThat(table.item()).isEqualTo("--filter=...{package}");
    assertThat(table.command())
        .containsExactly(
            "pnpm",
            "exec",
            "turbo",
            "run",
            "lint",
            "--log-prefix=task",
            "--log-order=grouped",
            "--continue=always",
            "--ui=stream");
  }

  @Test
  void rootConfigsAndTurboJsonRunEveryPackage() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("biome", TURBO_ROOT, QualityPreset.STANDARD, List.of())
            .orElseThrow();

    assertThat(table.full()).contains("turbo.json", "package.json", "biome.json");
  }

  @Test
  void aNonTurboProjectKeepsItsOwnLintTable() {
    final Project plain =
        new Project(
            Path.of("/work/app"),
            Language.TYPESCRIPT,
            BuildTool.PNPM,
            Framework.NONE,
            false,
            false,
            Path.of("/work/app"),
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);

    assertThat(
            PartialCheckDefaults.forGate("eslint", plain, QualityPreset.STANDARD, List.of())
                .orElseThrow()
                .command())
        .containsExactly("pnpm", "exec", "eslint", "--format", "json");
  }
}
