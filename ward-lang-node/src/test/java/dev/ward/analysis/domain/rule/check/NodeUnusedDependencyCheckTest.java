package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unused-dependency check: imports, scripts, typed packages and the root manifest. */
final class NodeUnusedDependencyCheckTest {

  private static final String MESSAGE = "unused {value}";
  private static final String DEPENDENCIES = "dependencies";

  @Test
  void reportsADependencyNoFileOfItsPackageImports() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEPENDENCIES)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import _ from 'lodash';\nexport const a = _;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json",
                        "{\n  \"dependencies\": {\n    \"lodash\": \"^4\",\n    \"unused-lib\": \"^1\"\n  }\n}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:4:5");
  }

  @Test
  void treatsAScriptMentionAsUse() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json",
                        "{\"scripts\":{\"lint\":\"eslint .\"},\"dependencies\":{\"eslint\":\"^9\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsATypesPackageAsUsedWhenItsPackageIs() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, "devDependencies")
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import _ from 'lodash';\nexport const a = _;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json",
                        "{\"dependencies\":{\"lodash\":\"^4\"},\"devDependencies\":{\"@types/lodash\":\"^4\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void countsEveryFileForTheRootManifest() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, "devDependencies")
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "packages/app/index.ts", "import v from 'vitest';\nexport const a = v;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"devDependencies\":{\"vitest\":\"^3\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void checksAWorkspaceOnlyAgainstItsOwnImports() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEPENDENCIES)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "root.ts", "export const a = 1;",
                        "pkg/index.ts", "export const b = 2;")),
                ProjectFixtures.manifests(
                    Map.of("pkg/package.json", "{\"dependencies\":{\"lodash\":\"^4\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("pkg/package.json:1:18");
  }
}
