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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The crap gate follows the coverage check, and Angular is not offered it. */
class NodeCrapCommandsTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;

  @TempDir Path root;

  @Test
  void crapIsPlacedAfterTheCoverageCheck() {
    final InitInputs inputs = inputs(NO_RUNNER, false);
    final Project project = project(BuildTool.NPM, Framework.NONE, TaskRunner.NONE);
    final List<InitGateOption> offered =
        new InitGeneralCatalog(inputs)
            .gates(project, QualityPreset.STANDARD).stream()
                .filter(gate -> List.of("vitest", "coverage", "crap").contains(gate.id()))
                .toList();

    assertThat(
            new NodeInitSupport()
                .crap(project, inputs).map(setup -> setup.apply(offered)).orElse(offered).stream()
                    .map(InitGateOption::id))
        .containsExactly("vitest", "coverage", "crap");
  }

  @Test
  void angularIsNotOffered() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(inputs(NO_RUNNER, false));

    assertThat(catalog.gates(project(BuildTool.NPM, Framework.ANGULAR, TaskRunner.NONE), null))
        .extracting(InitGateOption::id)
        .contains("ng-test")
        .doesNotContain("crap");
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
        root, Language.TYPESCRIPT, manager, framework, false, root, Set.of(), "24", runner, null);
  }
}
