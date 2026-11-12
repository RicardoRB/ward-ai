package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiLoadErrorsTest {

  private static String schemaRef(final String reference) {
    return "openapi: 3.0.0\ncomponents:\n  schemas:\n    A:\n      $ref: '" + reference + "'\n";
  }

  private static String firstMessage(final String text) {
    return FakeProject.of(text).load(FakeProject.ENTRY).errors().getFirst().message();
  }

  @Test
  void referencesThatFormALoopWithNoTargetAreAnError() {
    final String spec =
        schemaRef("#/components/schemas/B") + "    B:\n      $ref: '#/components/schemas/A'\n";

    final LoadResult result = FakeProject.of(spec).load(FakeProject.ENTRY);

    assertThat(result.loaded()).isFalse();
    assertThat(result.errors().getFirst().message()).contains("loop");
  }

  @Test
  void aMissingPointerIsAnErrorAtTheReference() {
    final LoadResult result = FakeProject.of(schemaRef("#/nope/x")).load(FakeProject.ENTRY);

    assertThat(result.loaded()).isFalse();
    final LoadError error = result.errors().getFirst();
    assertThat(error.file()).isEqualTo(FakeProject.ENTRY);
    assertThat(error.line() + ":" + error.column()).isEqualTo("5:13");
    assertThat(error.message()).contains("'#/nope/x'").contains("points at nothing");
  }

  @Test
  void aMissingFileIsAnErrorAtTheReference() {
    final LoadError error =
        FakeProject.of(schemaRef("gone.yaml")).load(FakeProject.ENTRY).errors().getFirst();

    assertThat(error.describe()).startsWith("openapi.yaml:5:");
    assertThat(error.message()).contains("gone.yaml");
  }

  @Test
  void aReferenceOutsideTheProjectIsAnErrorAndIsNeverRead() {
    final String spec = schemaRef("../secret.yaml") + "    B:\n      $ref: '/etc/passwd'\n";
    final FakeProject project = FakeProject.of(spec);

    final LoadResult result = project.load(FakeProject.ENTRY);

    assertThat(result.errors()).hasSize(2);
    assertThat(result.errors().getFirst().message()).contains("leaves the project");
    assertThat(project.reads()).containsExactly(FakeProject.ENTRY);
  }

  @Test
  void aRemoteReferenceIsAnErrorAndIsNeverFetched() {
    final FakeProject project = FakeProject.of(schemaRef("https://example.com/a.yaml#/A"));

    final LoadResult result = project.load(FakeProject.ENTRY);

    assertThat(result.errors().getFirst().message()).contains("does not fetch remote references");
    assertThat(project.reads()).containsExactly(FakeProject.ENTRY);
    assertThat(firstMessage(schemaRef("file:///etc/passwd"))).contains("unsupported scheme");
  }

  @Test
  void aSyntaxErrorInAReferencedFileNamesThatFile() {
    final FakeProject project = FakeProject.of(schemaRef("a.yaml"), "a.yaml", "a: 1\n  b: 2\n");

    final LoadError error = project.load(FakeProject.ENTRY).errors().getFirst();

    assertThat(error.file()).isEqualTo("a.yaml");
    assertThat(error.line()).isEqualTo(2);
  }

  @Test
  void rejectsDocumentsThatAreNotUsableOpenApi() {
    assertThat(firstMessage("swagger: \"2.0\"\n")).contains("Swagger 2.0 is not supported");
    assertThat(firstMessage("openapi: 3.2.0\n")).contains("OpenAPI 3.2.0 is not supported");
    assertThat(firstMessage("info: {}\n")).contains("not an OpenAPI document");
    assertThat(firstMessage("- a\n")).contains("not an OpenAPI document");
    assertThat(firstMessage("")).contains("empty");
    assertThat(new FakeProject(Map.of()).load(FakeProject.ENTRY).errors().getFirst().message())
        .contains("cannot be read");
  }

  @Test
  void loadsFromSyntaxTreesAndReportsAFileOutsideTheRun() {
    final Map<String, dev.ward.analysis.domain.ir.SyntaxNode> trees =
        Map.of(
            FakeProject.ENTRY,
            YamlParser.parse(schemaRef("a.yaml")).tree(),
            "a.yaml",
            YamlParser.parse("type: string\n").tree());

    assertThat(OpenApiDocuments.fromTrees(FakeProject.ENTRY, trees).loaded()).isTrue();
    final LoadResult without =
        OpenApiDocuments.fromTrees(
            FakeProject.ENTRY, Map.of(FakeProject.ENTRY, trees.get(FakeProject.ENTRY)));
    assertThat(without.errors().getFirst().message()).contains("cannot be read");
  }
}
