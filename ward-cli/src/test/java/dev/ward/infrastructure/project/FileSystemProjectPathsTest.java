package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemProjectPathsTest {
  private final FileSystemProjectPaths paths = new FileSystemProjectPaths();

  @TempDir Path temp;

  @Test
  void existenceChecksDistinguishDirectoriesAndFiles() throws IOException {
    final Path root = Files.createDirectories(temp.resolve("project/docs/guide"));
    Files.writeString(root.resolve("../readme.md"), "# readme");
    final Path project = temp.resolve("project");

    assertThat(paths.isDirectory(project, Path.of("docs/guide"))).isTrue();
    assertThat(paths.isDirectory(project, Path.of("docs/missing"))).isFalse();
    assertThat(paths.isRegularFile(project, Path.of("docs/readme.md"))).isTrue();
    assertThat(paths.isRegularFile(project, Path.of("docs/guide"))).isFalse();
  }
}
