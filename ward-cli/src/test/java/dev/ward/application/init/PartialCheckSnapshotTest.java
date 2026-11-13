package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.SupportedQualityGates;
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

/**
 * The partial-run table each toolchain's gates return today, pinned before the tables move behind
 * their toolchain support. A change to any of these tables is a change to what init writes.
 */
class PartialCheckSnapshotTest {
  private static final List<String> WHOLE_PROJECT = List.of("**");

  private static final Project PNPM =
      new Project(
          Path.of("/shop"),
          Language.TYPESCRIPT,
          BuildTool.PNPM,
          Framework.NONE,
          false,
          Path.of("/shop"),
          Set.of(),
          "24",
          TaskRunner.NONE,
          null);

  @Test
  void neutralGatesKeepTheirTablesForEveryProject() {
    assertThat(table("secrets", PNPM, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                WHOLE_PROJECT, List.of("ward-rules.toml"), List.of(), null, null));
    assertThat(PartialCheckDefaults.forOpenApi(SupportedQualityGates.OPENAPI_LINT, "api.yaml"))
        .contains(
            new PartialCheckDefaults.Table(List.of("api.yaml"), List.of(), List.of(), null, null));
  }

  private static PartialCheckDefaults.Table table(
      final String gate, final Project project, final QualityPreset preset) {
    return PartialCheckDefaults.forGate(gate, project, preset, List.of()).orElseThrow();
  }
}
