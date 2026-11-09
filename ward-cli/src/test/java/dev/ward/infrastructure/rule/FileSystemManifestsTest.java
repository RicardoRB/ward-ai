package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The manifest reader: Git-ignored manifests are dropped, and a Git failure drops none. */
class FileSystemManifestsTest {

  private static final Set<String> NAMES = Set.of("package.json");

  @TempDir Path root;

  @Test
  void dropsManifestsThatGitIgnores() throws IOException {
    write("package.json");
    write("examples/old/package.json");
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(
                0, "examples/old/package.json\u0000", "", FakeCommandExecutor.DURATION, null));

    assertThat(FileSystemManifests.read(root, NAMES, git).paths()).containsExactly("package.json");
    assertThat(git.commands.get(0))
        .containsExactly("git", "ls-files", "-o", "-i", "--exclude-standard", "-z");
  }

  @Test
  void keepsEveryManifestWhenGitFails() throws IOException {
    write("package.json");
    final FakeCommandExecutor git = new FakeCommandExecutor(128, "", "not a git repository");

    assertThat(FileSystemManifests.read(root, NAMES, git).paths()).containsExactly("package.json");
  }

  private void write(final String path) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{}");
  }
}
