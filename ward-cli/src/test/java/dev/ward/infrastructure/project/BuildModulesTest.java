package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Node workspace member with another language's build file is its own project, not a module. */
class BuildModulesTest {
  private static final String PACKAGE_JSON = "package.json";
  private static final String MEMBER = "{\"name\": \"member\"}";
  private static final String POM = "<project/>";
  private static final String PYPROJECT = "pyproject.toml";

  @TempDir Path root;

  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void mavenMemberOfNodeWorkspaceIsNotAModule() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"apps/*\"]}");
    tree.write("apps/api/" + PACKAGE_JSON, MEMBER);
    tree.write("apps/api/pom.xml", POM);

    assertThat(BuildModules.isModuleOf(tree.path(""), tree.path("apps/api"))).isFalse();
  }

  @Test
  void nodeMemberWithoutOtherBuildFileIsAModule() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"apps/*\"]}");
    tree.write("apps/web/" + PACKAGE_JSON, MEMBER);

    assertThat(BuildModules.isModuleOf(tree.path(""), tree.path("apps/web"))).isTrue();
  }

  @Test
  void uvWorkspaceMemberIsStillAModule() throws IOException {
    tree.write(PYPROJECT, "[tool.uv.workspace]\nmembers = [\"packages/*\"]\n");
    tree.write("packages/core/" + PYPROJECT, "[project]\nname = \"core\"\n");

    assertThat(BuildModules.isModuleOf(tree.path(""), tree.path("packages/core"))).isTrue();
  }

  @Test
  void pythonMemberOfNodeWorkspaceIsNotAModule() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"services/*\"]}");
    tree.write("services/ingest/" + PACKAGE_JSON, MEMBER);
    tree.write("services/ingest/" + PYPROJECT, "[project]\nname = \"ingest\"\n");

    assertThat(BuildModules.isModuleOf(tree.path(""), tree.path("services/ingest"))).isFalse();
  }
}
