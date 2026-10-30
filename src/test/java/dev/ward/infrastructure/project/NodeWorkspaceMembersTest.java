package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.WorkspaceMember;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Node workspace members come from the workspace patterns of the root, in every manager's form. */
class NodeWorkspaceMembersTest {
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path root;

  private final NodeWorkspaceMembers members = new NodeWorkspaceMembers();

  @Test
  void npmWorkspaceListsEveryMemberWithItsName() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    write("packages/ui/" + PACKAGE_JSON, "{\"name\": \"@repo/ui\"}");
    write("packages/core/tsconfig.json", "{}");

    assertThat(names()).containsExactly("packages/core", "@repo/ui");
  }

  @Test
  void yarnObjectFormListsMembers() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": {\"packages\": [\"./apps/*/\"]}}");
    write("apps/web/" + PACKAGE_JSON, "{\"name\": \"web\"}");

    assertThat(names()).containsExactly("web");
  }

  @Test
  void pnpmWorkspaceFileListsMembers() throws IOException {
    write(PACKAGE_JSON, "{}");
    write("pnpm-workspace.yaml", "packages:\n  - 'apps/*'\n");
    write("apps/web/" + PACKAGE_JSON, "{\"name\": \"web\"}");

    assertThat(names()).containsExactly("web");
  }

  @Test
  void configOnlyMemberIsListedWithItsDirectory() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    write("packages/eslint-config/" + PACKAGE_JSON, "{\"name\": \"@repo/eslint-config\"}");

    final List<WorkspaceMember> listed = members.members(root);

    assertThat(listed).hasSize(1);
    assertThat(listed.getFirst().directory()).isEqualTo(root.resolve("packages/eslint-config"));
    assertThat(listed.getFirst().project().root())
        .isEqualTo(root.resolve("packages/eslint-config"));
  }

  @Test
  void pathInsideAMemberNamesThatMemberAndOtherPathsNameNone() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    write("packages/ui/" + PACKAGE_JSON, "{\"name\": \"@repo/ui\"}");
    write("packages/ui/src/button.ts", "");

    assertThat(members.packageOf(root, root.resolve("packages/ui/src/button.ts")))
        .contains("@repo/ui");
    assertThat(members.packageOf(root, root.resolve(PACKAGE_JSON))).isEmpty();
    assertThat(members.packageOf(root, root.resolve("scripts/build.ts"))).isEmpty();
  }

  private List<String> names() {
    return members.members(root).stream().map(WorkspaceMember::name).toList();
  }

  private void write(final String name, final String content) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
