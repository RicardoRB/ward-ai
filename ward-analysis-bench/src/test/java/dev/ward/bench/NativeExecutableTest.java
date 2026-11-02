package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeExecutableTest {

  private static final byte[] ELF_FILE = {0x7F, 'E', 'L', 'F', 1, 2, 3};
  private static final byte[] JAR_FILE = {'P', 'K', 3, 4};
  private static final long ELF_FILE_SIZE = ELF_FILE.length;
  private static final int SHA256_HEX_LENGTH = 64;

  @TempDir Path dir;

  @Test
  void acceptsAnExecutableWithANativeHeader() throws IOException {
    final Path binary = executable("ward", ELF_FILE);

    final NativeExecutable executable = NativeExecutable.verify(binary);

    assertThat(executable.sizeBytes()).isEqualTo(ELF_FILE_SIZE);
    assertThat(executable.sha256()).hasSize(SHA256_HEX_LENGTH);
  }

  @Test
  void rejectsAJvmJarBeforeMeasuring() throws IOException {
    final Path jar = executable("ward.jar", JAR_FILE);

    assertThatThrownBy(() -> NativeExecutable.verify(jar))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a native executable")
        .hasMessageContaining("ward-cli/target/ward");
  }

  @Test
  void rejectsAMissingFileAndNamesTheExpectedExecutable() {
    assertThatThrownBy(() -> NativeExecutable.verify(dir.resolve("absent")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ward-cli/target/ward");
  }

  private Path executable(final String name, final byte[] content) throws IOException {
    final Path file = Files.write(dir.resolve(name), content);
    file.toFile().setExecutable(true);
    return file;
  }
}
