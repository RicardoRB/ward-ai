package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectFiles;
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

  private static final String MANIFEST = "package.json";
  private static final Set<String> NAMES = Set.of(MANIFEST);

  @TempDir Path root;

  @Test
  void dropsManifestsThatGitIgnores() throws IOException {
    write(MANIFEST);
    write("examples/old/package.json");
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(
                0, "examples/old/package.json\u0000", "", FakeCommandExecutor.DURATION, null));

    assertThat(FileSystemManifests.read(root, NAMES, git).paths()).containsExactly(MANIFEST);
    assertThat(git.commands.get(0))
        .containsExactly("git", "ls-files", "-o", "-i", "--exclude-standard", "--directory", "-z");
  }

  @Test
  void dropsManifestsInAnIgnoredDirectory() throws IOException {
    write(MANIFEST);
    write("examples/old/package.json");
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(0, "examples/old/\u0000", "", FakeCommandExecutor.DURATION, null));

    assertThat(FileSystemManifests.read(root, NAMES, git).paths()).containsExactly(MANIFEST);
  }

  @Test
  void keepsEveryManifestWhenGitFails() throws IOException {
    write(MANIFEST);
    final FakeCommandExecutor git = new FakeCommandExecutor(128, "", "not a git repository");

    assertThat(FileSystemManifests.read(root, NAMES, git).paths()).containsExactly(MANIFEST);
  }

  @Test
  void knowsEveryExistingFileButReadsOnlyTheWantedOnes() throws IOException {
    write(MANIFEST);
    write("src/styles.css");
    write("coverage/package.json");
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(0, "coverage/\u0000", "", FakeCommandExecutor.DURATION, null));

    final ProjectFiles files = FileSystemManifests.read(root, NAMES, git);

    assertThat(files.paths()).containsExactly(MANIFEST);
    assertThat(files.contains("src/styles.css")).isTrue();
    assertThat(files.existingPaths())
        .contains("src/styles.css", "coverage/package.json", MANIFEST);
  }

  private void write(final String path) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{}");
  }
}
