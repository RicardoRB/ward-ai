package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The referenced-optional-peer check: only optional peers that a file imports. */
final class NodeOptionalPeerCheckTest {

  private static final String MESSAGE = "optional {value} is imported";

  @Test
  void reportsAnOptionalPeerThatAFileImports() {
    final List<ProjectMatch> found =
        new NodeOptionalPeerCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import { ref } from 'vue';\nexport const a = ref;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json",
                        "{\n  \"peerDependencies\": {\n    \"react\": \"^18\",\n    \"vue\": \"^3\"\n  },\n"
                            + "  \"peerDependenciesMeta\": {\"vue\": {\"optional\": true}}\n}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:4:5");
    assertThat(ProjectFixtures.messages(found)).containsExactly("optional vue is imported");
  }

  @Test
  void ignoresAPeerThatIsNotOptional() {
    final List<ProjectMatch> found =
        new NodeOptionalPeerCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import { ref } from 'vue';\nexport const a = ref;")),
                ProjectFixtures.manifests(
                    Map.of("package.json", "{\"peerDependencies\":{\"vue\":\"^3\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void ignoresAnOptionalPeerThatNoFileImports() {
    final List<ProjectMatch> found =
        new NodeOptionalPeerCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "package.json",
                        "{\"peerDependencies\":{\"vue\":\"^3\"},\"peerDependenciesMeta\":{\"vue\":{\"optional\":true}}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
