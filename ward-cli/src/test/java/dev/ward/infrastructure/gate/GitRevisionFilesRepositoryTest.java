package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class GitRevisionFilesRepositoryTest {
  private static final String PROJECT = "services/lib";
  private final ProcessCommandExecutor executor = new ProcessCommandExecutor();
  private final GitRevisionFiles files = new GitRevisionFiles(executor);

  @TempDir Path repository;

  private Path project;

  @BeforeEach
  void init() {
    project = repository.resolve(PROJECT);
    git("init", "-q");
  }

  @Test
  void listsAndReadsNestedProjectFilesAtACommit() throws IOException {
    write("services/lib/src/main/java/A.java", "class A {}");
    write("services/other/B.java", "class B {}");
    final String first = commit("first");
    write("services/lib/src/main/java/C.java", "class C {}");
    Files.delete(project.resolve("src/main/java/A.java"));
    final String second = commit("second");

    assertThat(files.files(project, first, "src/main/java")).containsExactly("A.java");
    assertThat(files.files(project, second, "src/main/java")).containsExactly("C.java");
    assertThat(files.files(project, first, "")).containsExactly("src/main/java/A.java");
    assertThat(files.files(project, first, "missing")).isEmpty();
    assertThat(files.read(project, first, "src/main/java/A.java")).contains("class A {}");
    assertThat(files.read(project, second, "src/main/java/A.java")).isEmpty();
  }

  @Test
  void listsTagsReachableFromAndAtACommit() throws IOException {
    write("a.txt", "a");
    commit("first");
    git("tag", "v1.0.0");
    write("b.txt", "b");
    commit("second");
    git("tag", "v1.1.0");
    git("tag", "side", "HEAD");

    assertThat(files.tagsReachableFrom(repository, "HEAD"))
        .containsExactlyInAnyOrder("v1.0.0", "v1.1.0", "side");
    assertThat(files.tagsAt(repository, "HEAD")).containsExactlyInAnyOrder("v1.1.0", "side");
    assertThat(files.tagsAt(repository, "HEAD~1")).containsExactly("v1.0.0");
    assertThat(files.isShallow(repository)).isFalse();
  }

  @Test
  void nonRepositoryHasNothing(@TempDir final Path plain) {
    assertThat(files.tagsReachableFrom(plain, "HEAD")).isEmpty();
    assertThat(files.files(plain, "HEAD", "")).isEmpty();
    assertThat(files.read(plain, "HEAD", "a.txt")).isEmpty();
    assertThat(files.isShallow(plain)).isFalse();
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = repository.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private String commit(final String message) {
    git("add", "-A");
    git("commit", "-q", "-m", message);
    return executor.execute(List.of("git", "rev-parse", "HEAD"), repository).stdout().trim();
  }

  private void git(final String... arguments) {
    final List<String> command =
        new ArrayList<>(
            List.of(
                "git",
                "-c",
                "user.name=Test",
                "-c",
                "user.email=test@example.com",
                "-c",
                "commit.gpgsign=false",
                "-c",
                "tag.gpgsign=false"));
    command.addAll(List.of(arguments));
    executor.execute(command, repository);
  }
}
