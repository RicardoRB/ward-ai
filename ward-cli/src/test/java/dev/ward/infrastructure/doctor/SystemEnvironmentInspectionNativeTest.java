package dev.ward.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemEnvironmentInspectionNativeTest {
  @Test
  void findsNativeExecutableInModuleTarget(@TempDir final Path root) throws IOException {
    writeExecutable(root.resolve("ward-cli/target/ward"));

    assertThat(SystemEnvironmentInspection.nativeExecutablePresent(root)).isTrue();
  }

  @Test
  void findsNativeExecutableAtRootTarget(@TempDir final Path root) throws IOException {
    writeExecutable(root.resolve("target/ward"));

    assertThat(SystemEnvironmentInspection.nativeExecutablePresent(root)).isTrue();
  }

  @Test
  void ignoresNonExecutableNativeFile(@TempDir final Path root) throws IOException {
    final Path binary = root.resolve("ward-cli/target/ward");
    Files.createDirectories(binary.getParent());
    Files.writeString(binary, "");

    assertThat(SystemEnvironmentInspection.nativeExecutablePresent(root)).isFalse();
  }

  @Test
  void reportsNoNativeExecutableWhenNeitherLocationExists(@TempDir final Path root) {
    assertThat(SystemEnvironmentInspection.nativeExecutablePresent(root)).isFalse();
  }

  private static void writeExecutable(final Path binary) throws IOException {
    Files.createDirectories(binary.getParent());
    Files.writeString(binary, "");
    assertThat(new File(binary.toString()).setExecutable(true)).isTrue();
  }
}
