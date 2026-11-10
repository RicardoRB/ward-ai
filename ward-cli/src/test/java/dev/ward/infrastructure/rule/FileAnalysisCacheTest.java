package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileAnalysisCacheTest {
  @TempDir Path directory;

  @Test
  void missingEntryReadsAsEmpty() {
    assertThat(new FileAnalysisCache(directory.resolve("analysis")).read("absent")).isEmpty();
  }

  @Test
  void writtenEntryReadsBackAndOverwriteReplacesIt() throws IOException {
    final FileAnalysisCache cache = new FileAnalysisCache(directory.resolve("analysis"));

    cache.write("key", "first");
    cache.write("key", "second");

    assertThat(cache.read("key")).contains("second");
  }

  @Test
  void writeLeavesNoTemporaryFileBehind() throws IOException {
    final Path target = directory.resolve("analysis");
    new FileAnalysisCache(target).write("key", "value");

    try (Stream<Path> files = Files.list(target)) {
      assertThat(files.map(path -> path.getFileName().toString()).toList())
          .isEqualTo(List.of("key.json"));
    }
  }
}
