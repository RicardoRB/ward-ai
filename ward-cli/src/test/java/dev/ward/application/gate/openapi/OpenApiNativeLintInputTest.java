package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiNativeLintInputTest {

  @TempDir Path root;

  private NativeLintFixture fixture() {
    return new NativeLintFixture(root);
  }

  @Test
  void anInlineLintIgnoreIsRefusedByKeyAnywhereInTheLoadedFiles() throws IOException {
    fixture()
        .write(
            NativeLintFixture.SPEC,
            NativeLintFixture.CLEAN + "      x-lint-ignore: operation-tags\n");

    final GateResult result = fixture().lint();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("x-lint-ignore").contains(NativeLintFixture.SPEC);
  }

  @Test
  void anIgnoreInAReferencedFileIsRefusedToo() throws IOException {
    fixture()
        .write(NativeLintFixture.SPEC, "openapi: 3.0.3\npaths:\n  /pets:\n    $ref: 'pets.yaml'\n");
    fixture().write("api/pets.yaml", "get:\n  operationId: a\n  x-lint-ignore: all\n");

    assertThat(fixture().lint().summary()).contains("api/pets.yaml");
  }

  @Test
  void aMentionInACommentOrAStringIsNotAnIgnore() throws IOException {
    fixture()
        .write(
            NativeLintFixture.SPEC,
            NativeLintFixture.CLEAN
                + "      summary: 'x-lint-ignore is not allowed' # x-lint-ignore\n");

    assertThat(fixture().lint().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aSyntaxErrorIsAnExecutionErrorNamingFileAndLine() throws IOException {
    fixture()
        .write(
            NativeLintFixture.SPEC,
            "openapi: 3.0.3\npaths:\n  /pets:\n    get: 1\n  bad: 2\n x: 3\n");

    final GateResult result = fixture().lint();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).startsWith(NativeLintFixture.SPEC + ":6:");
  }

  @Test
  void unusableDocumentsAreExecutionErrors() throws IOException {
    final Map<String, String> specs =
        Map.of(
            "info: {}\n", "not an OpenAPI document",
            "swagger: \"2.0\"\n", "Swagger 2.0",
            "openapi: 3.0.0\npaths:\n  /a:\n    get: {}\n    get: {}\n", "duplicate key 'get'",
            "", "empty");
    for (final Map.Entry<String, String> spec : specs.entrySet()) {
      fixture().write(NativeLintFixture.SPEC, spec.getKey());

      final GateResult result = fixture().lint();

      assertThat(result.status()).as(spec.getValue()).isEqualTo(GateStatus.EXECUTION_ERROR);
      assertThat(result.summary()).contains(spec.getValue());
    }
  }

  @Test
  void referencesThatCannotBeUsedAreExecutionErrors() throws IOException {
    for (final String reference :
        List.of("gone.yaml", "https://example.com/a.yaml", "../../outside.yaml", "#/nope")) {
      fixture()
          .write(
              NativeLintFixture.SPEC,
              "openapi: 3.0.3\ncomponents:\n  schemas:\n    A:\n      $ref: '" + reference + "'\n");

      final GateResult result = fixture().lint();

      assertThat(result.status()).as(reference).isEqualTo(GateStatus.EXECUTION_ERROR);
      assertThat(result.summary()).contains(NativeLintFixture.SPEC + ":5:");
    }
  }
}
