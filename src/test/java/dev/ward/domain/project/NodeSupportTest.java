package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NodeSupportTest {
  @ParameterizedTest
  @ValueSource(strings = {"20", "18"})
  void olderNodeVersionWarns(final String version) {
    assertThat(NodeSupport.warning(node(BuildTool.NPM, version)))
        .hasValueSatisfying(
            message ->
                assertThat(message)
                    .contains("Node " + version)
                    .contains("Node 22 and 24")
                    .contains("may not work"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"22", "24", "26"})
  void supportedNodeVersionDoesNotWarn(final String version) {
    assertThat(NodeSupport.warning(node(BuildTool.NPM, version))).isEmpty();
  }

  @Test
  void bunProjectWithOldVersionDoesNotWarn() {
    assertThat(NodeSupport.warning(node(BuildTool.BUN, "20"))).isEmpty();
  }

  @Test
  void projectWithoutVersionDoesNotWarn() {
    assertThat(
            NodeSupport.warning(
                new Project(Path.of("app"), Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE)))
        .isEmpty();
  }

  private static Project node(final BuildTool tool, final String version) {
    final Path root = Path.of("app");
    return new Project(
        root,
        Language.TYPESCRIPT,
        tool,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        version,
        TaskRunner.NONE,
        null);
  }
}
