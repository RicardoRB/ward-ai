package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.jvm.types.JvmClass;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JvmClassLibrariesTest {
  private static final Path HOME = Path.of(System.getProperty("java.home"));

  @TempDir Path temp;

  private static byte[] bytesOf(final Class<?> type) throws IOException {
    try (InputStream in =
        ClassLoader.getSystemResourceAsStream(type.getName().replace('.', '/') + ".class")) {
      return in.readAllBytes();
    }
  }

  private Path jarOf(final Class<?>... types) throws IOException {
    final Path jar = temp.resolve("lib.jar");
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out)) {
      for (final Class<?> type : types) {
        jarOut.putNextEntry(new JarEntry(type.getName().replace('.', '/') + ".class"));
        jarOut.write(bytesOf(type));
        jarOut.closeEntry();
      }
    }
    return jar;
  }

  @Test
  void jdkImageFindsClassesWithoutAProcess() throws IOException {
    final Optional<JdkImageLibrary> opened = JdkImageLibrary.open(HOME.resolve("lib/modules"));
    assertThat(opened).isPresent();
    try (JdkImageLibrary image = opened.get()) {
      assertThat(image.find("java.util.Objects")).isPresent();
      assertThat(image.find("java.util.Map$Entry")).get().extracting(JvmClass::name)
          .isEqualTo("java.util.Map$Entry");
      assertThat(image.find("java.sql.Connection")).isPresent();
      assertThat(image.exists("java.lang.String")).isTrue();
      assertThat(image.find("java.lang.Missing")).isEmpty();
      assertThat(image.find("nopackage.Missing")).isEmpty();
      assertThat(image.find("Object")).isEmpty();
      assertThat(image.find("../etc/passwd")).isEmpty();
    }
  }

  @Test
  void jdkImageRejectsAFileThatIsNotAnImage() throws IOException {
    final Path fake = Files.write(temp.resolve("modules"), new byte[64]);

    assertThat(JdkImageLibrary.open(fake)).isEmpty();
    assertThat(JdkImageLibrary.open(temp.resolve("missing"))).isEmpty();
  }

  @Test
  void jarIndexIsCachedByContentHash() throws IOException {
    final Path jar = jarOf(String.class, java.util.List.class);
    final Path cache = temp.resolve("cache");

    try (JarLibrary first = new JarLibrary(jar, Optional.of(cache))) {
      assertThat(first.find("java.lang.String")).isPresent();
      assertThat(first.exists("java.lang.Integer")).isFalse();
    }
    assertThat(cache).isDirectoryContaining("glob:**/jar-*.idx");
    final Path moved = Files.move(jar, temp.resolve("renamed.jar"));
    try (JarLibrary warm = new JarLibrary(moved, Optional.of(cache))) {
      assertThat(warm.exists("java.util.List")).isTrue();
      assertThat(warm.find("java.util.List")).isPresent();
    }
  }

  @Test
  void directoryAndClasspathOrderAreHonoured() throws IOException {
    final Path classes = Files.createDirectories(temp.resolve("classes/app"));
    Files.write(classes.resolve("Sample.class"), bytesOf(Sample.class));
    final Path libs = Files.createDirectories(temp.resolve("libs"));
    Files.write(libs.resolve("notes.txt"), new byte[0]);

    try (JvmClassLibraries libraries =
        JvmClassLibraries.open(
            temp,
            List.of("classes", "libs/notes.txt", "missing.jar", libs.toString()),
            Optional.of(HOME),
            Optional.empty())) {
      assertThat(libraries.exists("app.Sample")).isTrue();
      assertThat(libraries.find("app.Sample")).isPresent();
      assertThat(new DirectoryLibrary(temp.resolve("classes")).find("app.Sample")).isPresent();
      assertThat(libraries.find("java.util.Objects")).isPresent();
      assertThat(libraries.find("java.util.Objects")).isSameAs(libraries.find("java.util.Objects"));
      assertThat(libraries.find("app.Absent")).isEmpty();
    }
  }

  @Test
  void findsTheJdkThroughJavaHomeThenTheRunningJdk() {
    assertThat(JvmClassLibraries.jdkHome(Optional.of(HOME.toString()), Optional.empty()))
        .contains(HOME);
    assertThat(JvmClassLibraries.jdkHome(Optional.of(temp.toString()), Optional.of(HOME.toString())))
        .contains(HOME);
    assertThat(JvmClassLibraries.jdkHome(Optional.empty(), Optional.empty())).isEmpty();
    assertThat(JvmClassLibraries.jdkHome(Optional.of(" "), Optional.of(temp.toString()))).isEmpty();
  }

  /** A class to put in a directory. */
  static final class Sample {}
}
