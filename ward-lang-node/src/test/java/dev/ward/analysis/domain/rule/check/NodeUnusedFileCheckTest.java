package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unused-file check: entries, reachability, framework silence and script entries. */
final class NodeUnusedFileCheckTest {

  private static final String MESSAGE = "unused file";
  private static final String SOURCE = "export const a = 1;";

  @Test
  void reportsAFileNoEntryReaches() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", SOURCE, "orphan.ts", "export const b = 2;")),
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
                ProjectFixtures.trees(Map.of("src/cli.ts", SOURCE)),
                ProjectFixtures.manifests(Map.of("package.json", "{\"bin\":\"./src/cli.ts\"}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void keepsAFileNamedByAScript() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("scripts/build.ts", SOURCE, "scripts/unused.ts", "export const b = 2;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"scripts\":{\"build\":\"tsx scripts/build.ts\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("scripts/unused.ts:1:1");
  }

  @Test
  void staysSilentInAFrameworkWorkspace() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("pages/home.ts", SOURCE)),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"dependencies\":{\"next\":\"^14.0.0\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsARemixPackageAsAFramework() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("app/routes.ts", SOURCE)),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"dependencies\":{\"@remix-run/react\":\"^2\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsInAWorkspaceThatUsesNoFramework() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("pkg/unused.ts", SOURCE)),
                ProjectFixtures.manifests(
                    Map.of("pkg/package.json", "{\"dependencies\":{\"lodash\":\"^4\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("pkg/unused.ts:1:1");
  }

  @Test
  void keepsAFileAPackageExportsByAWildcard() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("packages/ui/src/order.tsx", SOURCE)),
                ProjectFixtures.manifests(
                    Map.of(
                        "packages/ui/package.json",
                        "{\"name\":\"@repo/ui\",\"exports\":{\"./*\":\"./src/*.tsx\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void keepsAScriptThatTheRootIndexHtmlLoads() {
    final List<ProjectMatch> found =
        new NodeUnusedFileCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("src/main.tsx", SOURCE, "orphan.ts", "export const b = 2;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "index.html", "<script type=\"module\" src=\"/src/main.tsx\"></script>")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("orphan.ts:1:1");
  }
}
