package dev.ward.infrastructure.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The root package import-linter contracts name, read from the project's layout. */
class PythonPackagesTest {
  @TempDir Path root;

  @Test
  void rootPackageComesFromTheProjectNameUnderSrc() throws IOException {
    Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = \"order-service\"\n");
    Files.createDirectories(root.resolve("src/order_service"));
    Files.writeString(root.resolve("src/order_service/__init__.py"), "");
    Files.createDirectories(root.resolve("src/other"));
    Files.writeString(root.resolve("src/other/__init__.py"), "");

    assertThat(PythonPackages.root(project())).contains("order_service");
  }

  @Test
  void ambiguousProjectHasNoRootPackage() throws IOException {
    Files.createDirectories(root.resolve("alpha"));
    Files.writeString(root.resolve("alpha/__init__.py"), "");
    Files.createDirectories(root.resolve("beta"));
    Files.writeString(root.resolve("beta/__init__.py"), "");

    assertThat(PythonPackages.root(project())).isEqualTo(Optional.empty());
  }

  private Project project() {
    return new Project(root, Language.PYTHON, BuildTool.UV, Framework.NONE);
  }
}
