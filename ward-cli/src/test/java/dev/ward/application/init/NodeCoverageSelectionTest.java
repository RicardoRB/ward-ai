package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.node.PackageManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Node {@code coverage} check reads what a test runner wrote, so a selection needs both. */
class NodeCoverageSelectionTest {
  @TempDir Path root;

  private List<InitGateOption> resolve(final String... gates) {
    final Project project =
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    final InitSetupCatalog catalog =
        new InitSetupCatalog(
            new FakeEnvironmentInspection("npm"),
            new NodeInputs(
                directory -> PackageManifest.EMPTY,
                directory -> true,
                (directory, gate) -> Optional.empty()),
            null,
            null,
            "dev");
    return SelectedGates.resolve(
        catalog,
        project,
        new InitSelection(
            List.of("none"), List.of(gates), null, null, List.of(), false, null, false, null));
  }

  @Test
  void coverageWithoutATestGateIsRejectedNamingBothGates() {
    assertThatThrownBy(() -> resolve("coverage"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("coverage")
        .hasMessageContaining("vitest, jest or bun-test");
  }

  @Test
  void coverageFollowsTheTestGate() {
    assertThat(resolve("vitest", "coverage").stream().map(InitGateOption::id))
        .containsSubsequence("vitest", "coverage");
  }
}
