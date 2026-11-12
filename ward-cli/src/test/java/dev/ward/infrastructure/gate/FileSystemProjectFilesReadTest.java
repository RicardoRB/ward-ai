package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemProjectFilesReadTest {

  private final FileSystemProjectFiles files = new FileSystemProjectFiles();

  @TempDir Path root;

  @Test
  void readsAFileInsideTheRoot() throws IOException {
    Files.createDirectories(root.resolve("a"));
    Files.writeString(root.resolve("a/b.yaml"), "x: 1\n");

    assertThat(files.read(root, "a/b.yaml")).contains("x: 1\n");
  }

  @Test
  void aMissingFileADirectoryAndAnEscapingPathAreEmpty() throws IOException {
    Files.createDirectories(root.resolve("dir"));

    assertThat(files.read(root, "nope.yaml")).isEmpty();
    assertThat(files.read(root, "dir")).isEmpty();
    assertThat(files.read(root, "../outside.yaml")).isEmpty();
  }

  @Test
  void aFileThatIsNotUtf8IsEmpty() throws IOException {
    Files.write(root.resolve("bin"), new byte[] {(byte) 0xC3, (byte) 0x28});

    assertThat(files.read(root, "bin")).isEmpty();
  }
}
