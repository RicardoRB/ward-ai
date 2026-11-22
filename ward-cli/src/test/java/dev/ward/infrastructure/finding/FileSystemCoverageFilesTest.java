package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemCoverageFilesTest {
  @TempDir Path root;

  @Test
  void aGlobFindsTheReportsBelowAGeneratedDirectoryInPathOrder() throws IOException {
    for (final String name : new String[] {"b/c.xml", "a/c.xml", "a/other.xml"}) {
      Files.createDirectories(root.resolve("out").resolve(name).getParent());
      Files.writeString(root.resolve("out").resolve(name), "x");
    }

    assertThat(new FileSystemCoverageFiles().matching(root, "out/*/c.xml"))
        .containsExactly(root.resolve("out/a/c.xml"), root.resolve("out/b/c.xml"));
  }

  @Test
  void aGlobThatMatchesNothingFindsNothing() {
    assertThat(new FileSystemCoverageFiles().matching(root, "out/*/c.xml")).isEmpty();
  }
}
