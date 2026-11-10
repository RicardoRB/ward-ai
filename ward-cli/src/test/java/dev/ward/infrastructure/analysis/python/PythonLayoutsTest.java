package dev.ward.infrastructure.analysis.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.python.PythonProjectLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads the source roots of each Python layout the fixtures and a temporary project declare. */
final class PythonLayoutsTest {

  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

  @TempDir Path temp;

  @Test
  void srcLayoutHasTheSrcRoot() {
    assertThat(roots(FIXTURES.resolve("python-resolution"))).containsExactly("src/");
  }

  @Test
  void uvProjectWithSrcHasTheSrcRoot() {
    assertThat(roots(FIXTURES.resolve("python-uv"))).containsExactly("src/");
    assertThat(roots(FIXTURES.resolve("python-matrix/basic-uv"))).containsExactly("src/");
  }

  @Test
  void poetryPackagesHaveTheirFromRoot() {
    assertThat(roots(FIXTURES.resolve("python-matrix/basic-poetry"))).containsExactly("src/");
  }

  @Test
  void pipProjectWithSrcHasTheSrcRoot() {
    assertThat(roots(FIXTURES.resolve("python-matrix/basic-pip"))).containsExactly("src/");
  }

  @Test
  void uvWorkspaceHasTheRootOfEachMember() {
    assertThat(roots(FIXTURES.resolve("python-uv-workspace")))
        .containsExactly("packages/billing/src/", "packages/shop/src/");
  }

  @Test
  void flatProjectHasTheProjectRoot() throws IOException {
    Files.writeString(temp.resolve("pyproject.toml"), "[project]\nname = \"flat\"\n");
    Files.createDirectories(temp.resolve("flat"));
    Files.writeString(temp.resolve("flat/__init__.py"), "");

    assertThat(roots(temp)).containsExactly("");
  }

  @Test
  void unreadablePyprojectFallsBackToTheProjectRoot() throws IOException {
    Files.writeString(temp.resolve("pyproject.toml"), "[project\nbroken");

    assertThat(roots(temp)).containsExactly("");
  }

  private static List<String> roots(final Path projectRoot) {
    final PythonProjectLayout layout = PythonLayouts.read(projectRoot);
    return layout.sourceRoots();
  }
}
