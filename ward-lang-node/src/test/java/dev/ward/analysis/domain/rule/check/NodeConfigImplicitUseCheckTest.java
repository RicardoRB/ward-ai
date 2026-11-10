package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The packages that a tsconfig or an Angular workspace config names, which the dependency check
 * counts as used: the extended, plugin, JSX and builder packages, and tslib.
 */
final class NodeConfigImplicitUseCheckTest {

  private static final String MESSAGE = "unused {value}";
  private static final String PACKAGE = "package.json";
  private static final String DEV_DEPENDENCIES = "devDependencies";
  private static final String DEPENDENCIES = "dependencies";

  @Test
  void treatsTslibAsUsedWhenTheTsconfigSetsImportHelpers() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "tsconfig.json",
                        "{\"compilerOptions\": {\"importHelpers\": true}}",
                        PACKAGE,
                        "{\"devDependencies\":{\"tslib\":\"^2\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void stillReportsTslibWhenTheTsconfigDoesNotSetImportHelpers() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "tsconfig.json",
                        "{\"compilerOptions\": {\"importHelpers\": false}}",
                        PACKAGE,
                        "{\"devDependencies\":{\"tslib\":\"^2\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:1:21");
  }

  @Test
  void treatsATsconfigPluginAndItsPeersAsUsed() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "tsconfig.json",
                        "{\"compilerOptions\": {\"plugins\": [{\"name\": \"next\"}]}}",
                        PACKAGE,
                        "{\"dependencies\":{\"next\":\"^16\",\"react\":\"^19\",\"react-dom\":\"^19\"}}",
                        "node_modules/next/package.json",
                        "{\"name\":\"next\",\"peerDependencies\":{\"react\":\"^19\",\"react-dom\":\"^19\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsAnExtendedTsconfigPackageAsUsed() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "tsconfig.app.json",
                        "{\"extends\": \"@vue/tsconfig/tsconfig.dom.json\"}",
                        PACKAGE,
                        "{\"devDependencies\":{\"@vue/tsconfig\":\"^0.9\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsAJsxImportSourcePackageAsUsed() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "tsconfig.json",
                        "{\"compilerOptions\": {\"jsxImportSource\": \"@emotion/react\"}}",
                        PACKAGE,
                        "{\"dependencies\":{\"@emotion/react\":\"^11\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void aRelativeExtendsDoesNotUseAPackage() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "tsconfig.json",
                        "{\"extends\": \"./tsconfig.base.json\"}",
                        PACKAGE,
                        "{\"devDependencies\":{\"left-over\":\"^1\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:1:21");
  }

  @Test
  void treatsAnAngularBuilderPackageAndItsPeersAsUsed() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "angular.json",
                        "{\"projects\": {\"app\": {\"architect\": {\"build\": {\"builder\": \"@angular/build:application\"}}}}}",
                        PACKAGE,
                        "{\"devDependencies\":{\"@angular/build\":\"^21\",\"vitest\":\"^4\"}}",
                        "node_modules/@angular/build/package.json",
                        "{\"name\":\"@angular/build\",\"peerDependencies\":{\"vitest\":\"^4\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
