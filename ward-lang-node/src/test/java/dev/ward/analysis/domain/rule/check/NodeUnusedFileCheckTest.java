package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unused-file check: entries, reachability, framework silence and script entries. */
final class NodeUnusedFileCheckTest {

  private static final String MESSAGE = "unused file";

  @Test
  void reportsAFileNoEntryReaches() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "export const a = 1;", "orphan.ts", "export const b = 2;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("orphan.ts:1:1");
    assertThat(ProjectFixtures.messages(found)).containsExactly(MESSAGE);
  }

  @Test
  void keepsAFileReachedThroughAnImport() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts", "import { b } from './lib';\nexport const a = b;",
                        "lib.ts", "export const b = 2;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void keepsAFileNamedByAManifestEntry() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("src/cli.ts", "export const a = 1;")),
                ProjectFixtures.manifests(Map.of("package.json", "{\"bin\":\"./src/cli.ts\"}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void keepsAFileNamedByAScript() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "scripts/build.ts",
                        "export const a = 1;",
                        "scripts/unused.ts",
                        "export const b = 2;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"scripts\":{\"build\":\"tsx scripts/build.ts\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("scripts/unused.ts:1:1");
  }

  @Test
  void staysSilentInAFrameworkWorkspace() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("pages/home.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"dependencies\":{\"next\":\"^14.0.0\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsARemixPackageAsAFramework() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("app/routes.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"dependencies\":{\"@remix-run/react\":\"^2\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsInAWorkspaceThatUsesNoFramework() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("pkg/unused.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of("pkg/package.json", "{\"dependencies\":{\"lodash\":\"^4\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("pkg/unused.ts:1:1");
  }
}
