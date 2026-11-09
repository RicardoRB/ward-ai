package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unlisted binary check: a script command whose installed provider is not listed. */
final class NodeUnlistedBinaryCheckTest {

  private static final String MESSAGE = "unlisted {value}";
  private static final String ESLINT_BIN = "node_modules/eslint/package.json";
  private static final String ESLINT_PACKAGE =
      "{\"name\":\"eslint\",\"bin\":{\"eslint\":\"bin/eslint.js\"}}";

  @Test
  void reportsACommandWhoseInstalledProviderIsNotListed() {
    final List<ProjectMatch> found =
        new NodeUnlistedBinaryCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                project("{\"scripts\":{\"lint\":\"eslint .\"}}"));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:1:13");
    assertThat(ProjectFixtures.messages(found)).containsExactly("unlisted eslint");
  }

  @Test
  void keepsACommandWhoseProviderIsListed() {
    final List<ProjectMatch> found =
        new NodeUnlistedBinaryCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                project(
                    "{\"scripts\":{\"lint\":\"eslint .\"},\"devDependencies\":{\"eslint\":\"^9\"}}"));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsNothingForACommandWithNoInstalledProvider() {
    final List<ProjectMatch> found =
        new NodeUnlistedBinaryCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"scripts\":{\"lint\":\"eslint .\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void neverReportsASystemCommand() {
    final List<ProjectMatch> found =
        new NodeUnlistedBinaryCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                project("{\"scripts\":{\"run\":\"npm run build && node scripts/x.js\"}}"));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void readsTheBinNameOfAPackageWithAStringBin() {
    final List<ProjectMatch> found =
        new NodeUnlistedBinaryCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json", "{\"scripts\":{\"run\":\"cli x\"}}",
                        "node_modules/@acme/cli/package.json",
                            "{\"name\":\"@acme/cli\",\"bin\":\"./bin.js\"}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:1:13");
  }

  @Test
  void countsTheRootManifestAsListedForAWorkspace() {
    final List<ProjectMatch> found =
        new NodeUnlistedBinaryCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json",
                        "{\"devDependencies\":{\"eslint\":\"^9\"}}",
                        "packages/app/package.json",
                        "{\"scripts\":{\"lint\":\"eslint .\"}}",
                        ESLINT_BIN,
                        ESLINT_PACKAGE)));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  private static ProjectFiles project(final String manifest) {
    return ProjectFixtures.manifests(Map.of("package.json", manifest, ESLINT_BIN, ESLINT_PACKAGE));
  }
}
