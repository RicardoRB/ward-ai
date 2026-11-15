package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.jvm.JvmProjectName;
import dev.ward.infrastructure.project.node.NodeProjectName;
import dev.ward.infrastructure.project.python.PythonProjectName;
import dev.ward.infrastructure.project.swift.SwiftProjectName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectNameReadersTest {
  @TempDir Path root;

  @Test
  void mavenReadsTheRootArtifactIdNotTheParents() throws IOException {
    write(
        "pom.xml",
        "<project><parent><artifactId>parent</artifactId></parent>"
            + "<artifactId>shop</artifactId></project>");

    assertThat(new JvmProjectName().read(project(BuildTool.MAVEN))).contains("shop");
  }

  @Test
  void gradleReadsTheRootProjectName() throws IOException {
    write("settings.gradle.kts", "rootProject.name = \"shop\"\n");

    assertThat(new JvmProjectName().read(project(BuildTool.GRADLE))).contains("shop");
  }

  @Test
  void nodeReadsThePackageName() throws IOException {
    write("package.json", "{\"name\": \"shop\"}");

    assertThat(new NodeProjectName().read(project(BuildTool.NPM))).contains("shop");
  }

  @Test
  void pythonReadsTheProjectOrPoetryName() throws IOException {
    write("pyproject.toml", "[project]\nname = \"shop\"\n");
    assertThat(new PythonProjectName().read(project(BuildTool.UV))).contains("shop");

    write("pyproject.toml", "[tool.poetry]\nname = \"poet\"\n");
    assertThat(new PythonProjectName().read(project(BuildTool.POETRY))).contains("poet");
  }

  @Test
  void swiftReadsThePackageName() throws IOException {
    write("Package.swift", "let package = Package(\n    name: \"Shop\",\n    targets: [])\n");

    assertThat(new SwiftProjectName().read(project(BuildTool.SWIFTPM))).contains("Shop");
  }

  @Test
  void aMissingOrNamelessBuildFileGivesNoName() throws IOException {
    assertThat(new JvmProjectName().read(project(BuildTool.MAVEN))).isEmpty();
    assertThat(new JvmProjectName().read(project(BuildTool.GRADLE))).isEmpty();
    assertThat(new SwiftProjectName().read(project(BuildTool.XCODE))).isEmpty();
    write("pyproject.toml", "[tool.ruff]\n");
    assertThat(new PythonProjectName().read(project(BuildTool.UV))).isEqualTo(Optional.empty());
  }

  private Project project(final BuildTool tool) {
    return new Project(root, Language.JAVA, tool, Framework.NONE);
  }

  private void write(final String name, final String content) throws IOException {
    Files.writeString(root.resolve(name), content);
  }
}
