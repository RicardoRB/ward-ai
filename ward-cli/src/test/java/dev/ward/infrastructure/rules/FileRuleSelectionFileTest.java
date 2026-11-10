package dev.ward.infrastructure.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code ward-rules.toml} file adapter, on a real directory. */
class FileRuleSelectionFileTest {
  private static final String FILE = "ward-rules.toml";

  @TempDir private Path root;

  @Test
  void readIsEmptyWhenTheFileDoesNotExist() {
    assertThat(new FileRuleSelectionFile().read(root)).isEmpty();
  }

  @Test
  void writeReplacesTheFileAndLeavesNoTemporaryFile() throws IOException {
    final FileRuleSelectionFile adapter = new FileRuleSelectionFile();

    adapter.save(root, "version = 1\n# kept\n");
    adapter.save(root, "version = 1\n[select]\ninclude = []\n");

    assertThat(adapter.read(root)).contains("version = 1\n[select]\ninclude = []\n");
    try (Stream<Path> files = Files.list(root)) {
      assertThat(files.map(p -> p.getFileName().toString())).containsExactly(FILE);
    }
  }

  @Test
  void writeRefusesTextThatIsNotTomlAndKeepsTheFile() throws IOException {
    final FileRuleSelectionFile adapter = new FileRuleSelectionFile();
    adapter.save(root, "version = 1\n");

    assertThatThrownBy(() -> adapter.save(root, "[select\ninclude = ["))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("not valid TOML");

    assertThat(Files.readString(root.resolve(FILE))).isEqualTo("version = 1\n");
  }
}
