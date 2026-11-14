package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import dev.ward.analysis.domain.rule.ArchitectureRule;
import dev.ward.application.gate.ArchitectureFile;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where {@code ward-architecture.toml} is found and what is read from it. */
class ArchitectureFileReaderTest {
  @TempDir Path root;

  @Test
  void readsTheDefaultFileWhenItExists() throws IOException {
    ArchitectureFiles.write(
        root, "ward-architecture.toml", "version = 1\n\n" + ArchitectureFiles.VALID_RULE);

    final ArchitectureFile file = ArchitectureFileReader.read(root, null).orElseThrow();

    assertThat(file.problems()).isEmpty();
    assertThat(file.rules()).hasSize(1);
    final ArchitectureRule rule = file.rules().getFirst();
    assertThat(rule.name()).isEqualTo("domain-is-pure");
    assertThat(rule.constraint().kind()).isEqualTo(Kind.MUST_NOT_DEPEND_ON);
    assertThat(rule.constraint().target().directories())
        .containsExactly("infrastructure", "adapter");
    assertThat(rule.allowEmpty()).isFalse();
  }

  @Test
  void anAbsentDefaultFileIsNotAnError() {
    assertThat(ArchitectureFileReader.read(root, null)).isEmpty();
  }

  @Test
  void aConfiguredPathIsReadInsteadOfTheDefault() throws IOException {
    ArchitectureFiles.write(root, "ward-architecture.toml", "version = 1\n");
    ArchitectureFiles.write(
        root, "config/architecture.toml", "version = 1\n\n" + ArchitectureFiles.VALID_RULE);

    final ArchitectureFile file =
        ArchitectureFileReader.read(root, "config/architecture.toml").orElseThrow();

    assertThat(file.name()).isEqualTo("config/architecture.toml");
    assertThat(file.rules()).hasSize(1);
  }

  @Test
  void aConfiguredFileThatDoesNotExistNamesTheFile() {
    assertThat(ArchitectureFiles.problems(root, "config/architecture.toml"))
        .containsExactly("config/architecture.toml: the file does not exist");
  }

  @Test
  void aPathOutsideTheProjectIsRejected() {
    assertThat(ArchitectureFiles.problems(root, "../shared/architecture.toml"))
        .containsExactly("../shared/architecture.toml: the file must stay inside the project");
  }
}
