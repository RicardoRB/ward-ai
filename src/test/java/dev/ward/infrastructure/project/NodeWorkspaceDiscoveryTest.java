package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Node workspace members belong to their workspace root in detection and discovery. */
class NodeWorkspaceDiscoveryTest {
  private static final String PACKAGE_JSON = "package.json";
  private static final String MEMBER = "{\"name\": \"member\"}";
  private static final String FRONTEND = "frontend";
  private static final int MEMBERS = 3;

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void npmWorkspaceMembersAreGroupedUnderTheRoot() throws IOException {
    tree.write("frontend/" + PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    tree.write("frontend/packages/ui/" + PACKAGE_JSON, MEMBER);
    tree.write("frontend/packages/core/" + PACKAGE_JSON, MEMBER);
    tree.write("frontend/packages/api/" + PACKAGE_JSON, MEMBER);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(FRONTEND));
    assertThat(inspection.discover(root).getFirst().moduleCount()).isEqualTo(MEMBERS);
  }

  @Test
  void yarnObjectFormListsMembers() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": {\"packages\": [\"./apps/*/\"]}}");
    tree.write("apps/web/" + PACKAGE_JSON, MEMBER);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(""));
  }

  @Test
  void pnpmWorkspaceFileListsMembers() throws IOException {
    tree.write("frontend/" + PACKAGE_JSON, "{}");
    tree.write(
        "frontend/pnpm-workspace.yaml",
        "packages:\n  - \"packages/*\"\n  - '!packages/ignored'\ncatalog:\n  - apps/*\n");
    tree.write("frontend/packages/core/" + PACKAGE_JSON, MEMBER);
    tree.write("frontend/apps/web/" + PACKAGE_JSON, MEMBER);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactly(tree.path(FRONTEND), tree.path("frontend/apps/web"));
  }

  @Test
  void detectFromAMemberReportsTheWorkspaceRoot() throws IOException {
    tree.write("frontend/" + PACKAGE_JSON, "{}");
    tree.write("frontend/pnpm-workspace.yaml", "packages:\n  - packages/*\n");
    tree.write("frontend/packages/ui/" + PACKAGE_JSON, MEMBER);

    assertThat(inspection.detect(root.resolve("frontend/packages/ui")).map(Project::root))
        .contains(tree.path(FRONTEND));
  }

  @Test
  void unlistedPackageIsItsOwnProject() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\", \"!packages/legacy\"]}");
    tree.write("packages/ui/" + PACKAGE_JSON, MEMBER);
    tree.write("examples/demo/" + PACKAGE_JSON, MEMBER);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactly(tree.path(""), tree.path("examples/demo"));
    assertThat(inspection.detect(root.resolve("examples/demo")).map(Project::root))
        .contains(tree.path("examples/demo"));
  }

  @Test
  void typeScriptMemberMakesTheRootTypeScript() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    tree.write("packages/ui/" + PACKAGE_JSON, MEMBER);
    tree.write("packages/ui/tsconfig.json", "{}");

    assertThat(inspection.detect(root).map(Project::language)).contains(Language.TYPESCRIPT);
  }

  @Test
  void javaScriptOnlyWorkspaceIsJavaScript() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    tree.write("packages/ui/" + PACKAGE_JSON, MEMBER);
    tree.write("examples/demo/tsconfig.json", "{}");

    assertThat(inspection.detect(root).map(Project::language)).contains(Language.JAVASCRIPT);
  }
}
