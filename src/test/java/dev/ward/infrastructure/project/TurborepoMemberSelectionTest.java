package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.WorkspaceMember;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Which workspace packages a Turborepo plans: a package with code below its own folder (even
 * without src/) is planned, a package with root-level configuration only is not, and a package that
 * belongs to another language is not a Node member at all.
 */
class TurborepoMemberSelectionTest {
  private static final String ROOT_MANIFEST = "{\"workspaces\": [\"apps/*\", \"packages/*\"]}";

  @TempDir Path root;

  @Test
  void packageWithCodeInAppFolderIsPlannedWithoutSrc() throws IOException {
    write("package.json", ROOT_MANIFEST);
    write("apps/web/package.json", "{\"name\": \"web\"}");
    write("apps/web/app/page.tsx", "export default 1;\n");

    assertThat(names()).containsExactly("web");
    assertThat(new FileSystemNodeSources().hasSourceTree(root.resolve("apps/web"))).isTrue();
  }

  @Test
  void packageWithRootLevelConfigurationOnlyHasNoSourceTree() throws IOException {
    write("package.json", ROOT_MANIFEST);
    write("packages/eslint-config/package.json", "{\"name\": \"@repo/eslint-config\"}");
    write("packages/eslint-config/base.js", "export default [];\n");

    assertThat(new FileSystemNodeSources().hasSourceTree(root.resolve("packages/eslint-config")))
        .isFalse();
  }

  @Test
  void mavenPackageInsideAWorkspaceIsNotANodeMember() throws IOException {
    write("package.json", ROOT_MANIFEST);
    write("apps/web/package.json", "{\"name\": \"web\"}");
    write("apps/api/package.json", "{\"name\": \"api\"}");
    write("apps/api/pom.xml", "<project/>\n");
    write("apps/api/src/main/java/App.java", "class App {}\n");

    assertThat(names()).containsExactly("web");
  }

  private List<String> names() {
    return new NodeWorkspaceMembers().members(root).stream().map(WorkspaceMember::name).toList();
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
