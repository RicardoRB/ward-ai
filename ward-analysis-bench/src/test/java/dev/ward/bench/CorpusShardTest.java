package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Splits a corpus into shards: source files by extension, hidden paths never copied. */
class CorpusShardTest {

  @TempDir Path root;

  @Test
  void listsOnlyTheSourceFilesOfTheExtensionsSorted() throws IOException {
    write("src/B.java");
    write("src/A.java");
    write("pom.xml");
    write(".git/config.java");

    assertThat(CorpusShard.sourceFiles(CorpusShard.files(root), Set.of(".java")))
        .containsExactly("src/A.java", "src/B.java");
  }

  @Test
  void splitsFilesIntoShardsOfTheShardSize() {
    final List<String> files =
        java.util.stream.IntStream.range(0, CorpusShard.SIZE + 1)
            .mapToObj(i -> "F" + i + ".java")
            .toList();

    final List<List<String>> shards = CorpusShard.of(files);

    assertThat(shards).hasSize(2);
    assertThat(shards.get(0)).hasSize(CorpusShard.SIZE);
    assertThat(shards.get(1)).containsExactly(files.get(CorpusShard.SIZE));
  }

  @Test
  void aShardHoldsItsOwnSourcesAndEveryOtherFile() throws IOException {
    write("src/A.java");
    write("src/B.java");
    write("pom.xml");
    final Path work = root.resolveSibling(root.getFileName() + "-shard");

    CorpusShard.populate(
        root,
        work,
        CorpusShard.files(root),
        Set.of("src/A.java"),
        Set.of("src/A.java", "src/B.java"));

    assertThat(work.resolve("src/A.java")).exists();
    assertThat(work.resolve("src/B.java")).doesNotExist();
    assertThat(work.resolve("pom.xml")).exists();
    assertThat(work.resolve("src")).isDirectory();
  }

  @Test
  void extensionKeepsOnlyTheLastDottedSuffixOfTheFileName() {
    assertThat(CorpusShard.extension("a/b.c/Foo.java")).isEqualTo(".java");
    assertThat(CorpusShard.extension("a/b.c/Makefile")).isEmpty();
  }

  private void write(final String relative) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "x\n");
  }
}
