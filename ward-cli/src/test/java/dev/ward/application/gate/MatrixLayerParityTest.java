package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine's layer verdict on the seeded layer violations of the Python and Java matrices equals
 * the file that Import Linter and the generated ArchUnit test fail on.
 */
class MatrixLayerParityTest {
  private static final Path RESOURCES = Path.of("src/test/resources");

  @TempDir Path root;

  @Test
  void theFastApiRouterThatImportsARepositoryIsTheOnlyPythonViolation() throws IOException {
    copy(RESOURCES.resolve("fixtures/python-matrix/fastapi-uv"), root);
    copy(RESOURCES.resolve("python-matrix/violations/fastapi-uv"), root);
    final Project project = new Project(root, Language.PYTHON, BuildTool.UV, Framework.FASTAPI);

    assertThat(
            flagged(
                project,
                ArchitectureLayers.forStyle("layered", Framework.FASTAPI),
                new PythonDependencyAdapters()))
        .containsExactly("app/routers/cross.py");
  }

  @Test
  void theDirectRepositoryImportInTheJavaControllerIsTheOnlyJavaViolation() throws IOException {
    copy(RESOURCES.resolve("fixtures/java-matrix/maven"), root);
    copy(RESOURCES.resolve("fixtures/java-matrix/sources/base"), root);
    copy(RESOURCES.resolve("java-matrix/violations/common"), root);
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);

    assertThat(
            flagged(
                project,
                ArchitectureLayers.forStyle("layered", Framework.NONE),
                new JvmDependencyAdapters()))
        .containsExactly("src/main/java/demo/controller/DirectController.java");
  }

  private static Set<String> flagged(
      final Project project, final LayerBoundary boundary, final DependencyAdapters adapter) {
    return LayerPorts.findings(project, boundary, new ProjectDependencyAdapters(List.of(adapter)))
        .stream()
        .map(Finding::file)
        .collect(Collectors.toSet());
  }

  private static void copy(final Path source, final Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path destination = target.resolve(source.relativize(file).toString());
        Files.createDirectories(destination.getParent());
        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
}
