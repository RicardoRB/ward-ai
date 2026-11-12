package dev.ward.infrastructure.dependency.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipRequirementsSourceTest {
  private static DependencyInventory read(final Path dir, final BuildTool tool) {
    return new PythonDependencySource()
        .read(new Project(dir.toAbsolutePath(), Language.PYTHON, tool, Framework.NONE));
  }

  @Test
  void anUnpinnedRequirementNamesTheFileTheLineAndWhyItFails(@TempDir final Path dir)
      throws IOException {
    Files.writeString(dir.resolve("requirements.txt"), "idna==3.10\ndjango>=4\n");

    assertThatThrownBy(() -> read(dir, BuildTool.PIP))
        .hasMessageContaining("requirements.txt")
        .hasMessageContaining("line 2")
        .hasMessageContaining("django>=4")
        .hasMessageContaining("not an exact version");
  }

  @Test
  void editableUrlAndVcsRequirementsAreErrors(@TempDir final Path dir) throws IOException {
    for (final String line :
        new String[] {
          "-e .", "git+https://github.com/a/b.git#egg=b", "pkg @ https://x/y.whl", "./local"
        }) {
      Files.writeString(dir.resolve("requirements.txt"), line + "\n");

      assertThatThrownBy(() -> read(dir, BuildTool.PIP))
          .as(line)
          .isInstanceOf(DependencyInventoryException.class);
    }
  }

  @Test
  void anIncludeOutsideTheProjectIsAnError(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("requirements.txt"), "-r ../other.txt\n");

    assertThatThrownBy(() -> read(dir, BuildTool.PIP)).hasMessageContaining("not in the project");
  }

  @Test
  void aViaAnnotationNamingAPackageMakesTheEntryTransitive(@TempDir final Path dir)
      throws IOException {
    Files.writeString(
        dir.resolve("requirements.txt"),
        "idna==3.10\n    # via requests\nrequests==2.32.3\n    # via -r requirements.in\n");

    final List<Dependency> entries = read(dir, BuildTool.PIP).modules().getFirst().dependencies();

    assertThat(entries)
        .extracting(e -> e.id().name() + ":" + e.relation())
        .containsExactly("idna:TRANSITIVE", "requests:DIRECT");
    assertThat(entries).allMatch(e -> e.requiredBy().isEmpty());
  }
}
