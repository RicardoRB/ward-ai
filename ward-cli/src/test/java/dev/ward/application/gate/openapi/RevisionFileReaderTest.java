package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.domain.gate.RevisionFiles;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RevisionFileReaderTest {
  private static final Path ROOT = Path.of("/repo");
  private static final String COMMIT = "abc123";
  private static final String SPEC =
      """
      openapi: 3.0.3
      info: {title: t, version: '1'}
      paths: {}
      components:
        schemas:
          Pet:
            $ref: 'schemas/pet.yml'
      """;

  private static RevisionFiles committed(final Map<String, String> files) {
    return new RevisionFiles() {
      @Override
      public Optional<String> resolve(final Path root, final String revision) {
        return Optional.of(COMMIT);
      }

      @Override
      public Optional<String> mergeBase(final Path root, final String first, final String second) {
        return Optional.of(COMMIT);
      }

      @Override
      public boolean exists(final Path root, final String commit, final String path) {
        return COMMIT.equals(commit) && files.containsKey(path);
      }

      @Override
      public Optional<String> read(final Path root, final String commit, final String path) {
        return exists(root, commit, path) ? Optional.of(files.get(path)) : Optional.empty();
      }
    };
  }

  @Test
  void aReferencedFileIsReadAtTheCommit() {
    final RevisionFileReader reader =
        new RevisionFileReader(
            committed(
                Map.of("openapi.yml", SPEC, "schemas/pet.yml", "type: object\nrequired: [name]\n")),
            ROOT,
            COMMIT);

    final LoadResult result = OpenApiDocuments.load("openapi.yml", reader);

    assertThat(result.loaded()).isTrue();
    assertThat(result.document().orElseThrow().files()).containsKey("schemas/pet.yml");
  }

  @Test
  void aReferencedFileAbsentAtTheCommitIsAnErrorNamingIt() {
    final RevisionFileReader reader =
        new RevisionFileReader(committed(Map.of("openapi.yml", SPEC)), ROOT, COMMIT);

    final LoadResult result = OpenApiDocuments.load("openapi.yml", reader);

    assertThat(result.loaded()).isFalse();
    assertThat(result.errors().getFirst().message()).contains("schemas/pet.yml");
  }
}
