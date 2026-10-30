package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Stryker packages the {@code stryker} gate adds, which depend on the test runner. */
class NodeStrykerPackagesTest {
  private static final String CORE = "@stryker-mutator/core";

  @TempDir Path root;

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
  }

  @Test
  void vitestProjectGetsTheCoreAndTheVitestRunnerAtOneVersion() {
    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(
            Language.TYPESCRIPT, gates("vitest", "stryker"), PackageManifest.EMPTY);

    assertThat(missing)
        .extracting(NodePackageCatalog.Package::name)
        .containsExactly("vitest", "@vitest/coverage-v8", CORE, "@stryker-mutator/vitest-runner");
    assertThat(missing)
        .filteredOn(tool -> tool.name().startsWith("@stryker-mutator/"))
        .extracting(NodePackageCatalog.Package::version)
        .containsExactly("10.1.1", "10.1.1");
  }

  @Test
  void jestProjectGetsTheJestRunner() {
    assertThat(
            NodePackageCatalog.missing(
                Language.JAVASCRIPT, gates("jest", "stryker"), PackageManifest.EMPTY))
        .extracting(NodePackageCatalog.Package::name)
        .containsExactly(CORE, "@stryker-mutator/jest-runner");
  }

  @Test
  void bunProjectGetsOnlyTheCore() {
    assertThat(
            NodePackageCatalog.missing(
                Language.TYPESCRIPT, gates("bun-test", "stryker"), PackageManifest.EMPTY))
        .extracting(NodePackageCatalog.Package::name)
        .containsExactly(CORE);
  }

  @Test
  void runnerPluginIsNotAddedWithoutStryker() {
    assertThat(
            NodePackageCatalog.missing(Language.TYPESCRIPT, gates("jest"), PackageManifest.EMPTY))
        .isEmpty();
  }

  @Test
  void nodeTwentyIsIncompatibleWithStryker() {
    final PlannedFileChange plan =
        new NodeBuildToolConfigurator(
                new FileSystemPackageManifests(), new FakeCommandExecutor(0, "", ""))
            .plan(project("20"), gates("bun-test", "stryker"), QualityPreset.STRICT, false);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.CONFLICT);
    assertThat(plan.details())
        .singleElement()
        .asString()
        .startsWith("INCOMPATIBLE " + CORE)
        .contains(">=22.0.0");
  }

  @Test
  void nodeTwentyFourAddsStryker() {
    final PlannedFileChange plan =
        new NodeBuildToolConfigurator(
                new FileSystemPackageManifests(), new FakeCommandExecutor(0, "", ""))
            .plan(project("24"), gates("bun-test", "stryker"), QualityPreset.STRICT, false);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.MODIFY);
    assertThat(plan.details()).contains("add devDependency " + CORE + " 10.1.1");
  }

  private Project project(final String node) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.BUN,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        node,
        TaskRunner.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
