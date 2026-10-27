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
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which test gate a Node project gets from the runner it declares and its framework. */
class NodeTestRunnerSelectionTest {
  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({
    "ANGULAR, NPM, '', ng-test",
    "ANGULAR, NPM, vitest, ng-test",
    "ANGULAR, NPM, jest, jest",
    "NONE, NPM, '', vitest",
    "NONE, NPM, jest, jest",
    "NONE, NPM, vitest, vitest",
    "NONE, BUN, '', bun-test"
  })
  void testGateFollowsDeclaredRunnerAndFramework(
      final Framework framework,
      final BuildTool manager,
      final String declared,
      final String gate) {
    final PackageManifest manifest =
        declared.isEmpty()
            ? PackageManifest.EMPTY
            : new PackageManifest(Map.of(declared, "1.0.0"), null, null);

    assertThat(NodeTools.testGate(project(manager, framework), manifest)).isEqualTo(gate);
  }

  @Test
  void angularProjectOffersNgTestWithCoverage() {
    final Project project = project(BuildTool.NPM, Framework.ANGULAR);

    assertThat(catalog().presetGates(project, QualityPreset.STANDARD)).contains("ng-test");
    assertThat(catalog().gates(project, null))
        .filteredOn(gate -> "ng-test".equals(gate.id()))
        .singleElement()
        .satisfies(
            gate -> assertThat(commandOf(gate)).isEqualTo("npx ng test --watch=false --coverage"));
  }

  @Test
  void angularProjectWithDeclaredVitestOffersNoVitestGate() {
    final Project project = project(BuildTool.NPM, Framework.ANGULAR);
    final PackageManifest manifest = new PackageManifest(Map.of("vitest", "4.1.12"), null, null);

    assertThat(NodeTools.testGate(project, manifest)).isEqualTo("ng-test");
  }

  private static String commandOf(final InitGateOption gate) {
    return String.join(" ", gate.command());
  }

  private InitGeneralCatalog catalog() {
    return new InitGeneralCatalog(
        new InitInputs(
            new FakeEnvironmentInspection("npm", "pnpm", "yarn"),
            directory -> PackageManifest.EMPTY,
            directory -> false));
  }

  private Project project(final BuildTool manager, final Framework framework) {
    return new Project(
        root, Language.TYPESCRIPT, manager, framework, false, false, root, Set.of(), "24");
  }
}
