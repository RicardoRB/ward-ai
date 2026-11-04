package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtJavaDependencyResolverTest {

  @TempDir Path dir;

  @Test
  void readsEntriesSeparatedByThePathSeparatorAndByNewlines() throws IOException {
    final Path file = dir.resolve("classpath.txt");
    Files.writeString(
        file, "/libs/a.jar" + File.pathSeparator + "/libs/b.jar\n\n/libs/c.jar\r\n  \n");

    assertThat(JdtJavaDependencyResolver.classpathEntries(file))
        .isEqualTo(List.of("/libs/a.jar", "/libs/b.jar", "/libs/c.jar"));
  }
}
