package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** uv workspace members belong to their workspace root in detection and discovery. */
class PythonWorkspaceDiscoveryTest {
  private static final String PYPROJECT = "pyproject.toml";
  private static final String MEMBER = "[project]\nname = \"member\"\n";
  private static final String WORKSPACE = "[tool.uv.workspace]\nmembers = [\"packages/*\"]\n";
  private static final int MEMBERS = 2;

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void workspaceMembersAreGroupedUnderTheRoot() throws IOException {
    tree.write(PYPROJECT, WORKSPACE);
    tree.write("packages/core/" + PYPROJECT, MEMBER);
    tree.write("packages/api/" + PYPROJECT, MEMBER);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(""));
    assertThat(inspection.discover(root).getFirst().moduleCount()).isEqualTo(MEMBERS);
  }

  @Test
  void detectInsideMemberResolvesToTheWorkspaceRoot() throws IOException {
    tree.write(PYPROJECT, WORKSPACE);
    tree.write("packages/core/" + PYPROJECT, MEMBER);

    final Project project = inspection.detect(tree.path("packages/core")).orElseThrow();

    assertThat(project.root()).isEqualTo(tree.path(""));
    assertThat(project.buildTool()).isEqualTo(BuildTool.UV);
  }

  @Test
  void excludedMemberIsListedOnItsOwn() throws IOException {
    tree.write(PYPROJECT, WORKSPACE + "exclude = [\"packages/legacy\"]\n");
    tree.write("packages/core/" + PYPROJECT, MEMBER);
    tree.write("packages/legacy/" + PYPROJECT, MEMBER);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactlyInAnyOrder(tree.path(""), tree.path("packages/legacy"));
  }
}
