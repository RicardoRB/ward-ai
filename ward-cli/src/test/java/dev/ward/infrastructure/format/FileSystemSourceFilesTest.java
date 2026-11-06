package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.format.SourceText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class FileSystemSourceFilesTest {
  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final byte[] NOT_UTF8 = {(byte) 0xFF, (byte) 0xFE, 'a'};
  private static final String FILE_NAME = "A.java";

  @TempDir Path folder;

  @Test
  void readsTheByteOrderMarkSeparately() throws IOException {
    final Path file = folder.resolve(FILE_NAME);
    Files.write(file, withMark("a"));

    assertThat(new FileSystemSourceFiles().read(file)).isEqualTo(new SourceText("a", true, false));
  }

  @Test
  void writesTheMarkWhenAskedAndLeavesNoTemporaryFile() throws IOException {
    final Path file = folder.resolve(FILE_NAME);
    Files.writeString(file, "old\n", StandardCharsets.UTF_8);

    new FileSystemSourceFiles().persist(file, "new\n", true);

    assertThat(Files.readAllBytes(file)).startsWith(BOM);
    assertThat(new FileSystemSourceFiles().read(file).text()).isEqualTo("new\n");
    try (Stream<Path> names = Files.list(folder)) {
      assertThat(names.map(name -> name.getFileName().toString())).containsExactly(FILE_NAME);
    }
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void keepsThePermissionsOfTheFile() throws IOException {
    final Path file = folder.resolve("run.py");
    Files.writeString(file, "x\n");
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-x---"));

    new FileSystemSourceFiles().persist(file, "y\n", false);

    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        .isEqualTo("rwxr-x---");
  }

  @Test
  void refusesAFileThatIsNotUtf8() throws IOException {
    final Path file = folder.resolve("bad.txt");
    Files.write(file, NOT_UTF8);

    assertThatThrownBy(() -> new FileSystemSourceFiles().read(file))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("as UTF-8");
  }

  private static byte[] withMark(final String text) {
    final byte[] body = text.getBytes(StandardCharsets.UTF_8);
    final byte[] bytes = new byte[BOM.length + body.length];
    System.arraycopy(BOM, 0, bytes, 0, BOM.length);
    System.arraycopy(body, 0, bytes, BOM.length, body.length);
    return bytes;
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void aSymbolicLinkIsReportedAndNeverRewritten() throws IOException {
    final Path target = folder.resolve("real.java");
    Files.writeString(target, "class A{}\n");
    final Path link = folder.resolve("link.java");
    Files.createSymbolicLink(link, target);
    final FileSystemSourceFiles files = new FileSystemSourceFiles();

    assertThat(files.read(link).symbolicLink()).isTrue();
    assertThat(files.read(target).symbolicLink()).isFalse();
    assertThatThrownBy(() -> files.persist(link, "class B{}\n", false))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("symbolic link, not rewritten");
    assertThat(Files.isSymbolicLink(link)).isTrue();
    assertThat(Files.readString(target)).isEqualTo("class A{}\n");
  }
}
