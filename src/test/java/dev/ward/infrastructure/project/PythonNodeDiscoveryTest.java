package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A directory with a Python marker and a Node marker is two projects in discovery only. */
class PythonNodeDiscoveryTest {
  private static final String PYPROJECT = "[project]\nname = \"web\"\n";
  private static final String PACKAGE_JSON = "{\"name\": \"web\"}";
  private static final String POM = "<project/>";

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void pythonAndNodeProjectsShareOneRoot() throws IOException {
    tree.write("web/pyproject.toml", PYPROJECT);
    tree.write("web/package.json", PACKAGE_JSON);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactly(tree.path("web"), tree.path("web"));
    assertThat(inspection.discover(root))
        .extracting(discovered -> discovered.project().language())
        .containsExactlyInAnyOrder(Language.PYTHON, Language.JAVASCRIPT);
  }

  @Test
  void detectInTheSharedDirectoryStillReportsTheNodeProject() throws IOException {
    tree.write("web/pyproject.toml", PYPROJECT);
    tree.write("web/package.json", PACKAGE_JSON);

    final Project project = inspection.detect(tree.path("web")).orElseThrow();

    assertThat(project.language()).isEqualTo(Language.JAVASCRIPT);
    assertThat(project.buildTool()).isEqualTo(BuildTool.NPM);
  }

  @Test
  void mavenAndNodeInOneDirectoryStayOneProject() throws IOException {
    tree.write("api/pom.xml", POM);
    tree.write("api/package.json", PACKAGE_JSON);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path("api"));
  }
}
