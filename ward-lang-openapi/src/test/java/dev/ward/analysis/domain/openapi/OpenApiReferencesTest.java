package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiReferencesTest {

  @Test
  void resolvesALocalReference() {
    final String spec =
        """
        openapi: 3.0.0
        paths:
          /a:
            get:
              responses:
                '200':
                  $ref: '#/components/responses/Ok'
        components:
          responses:
            Ok:
              description: ok
        """;
    final OpenApiDocument document = FakeProject.of(spec).loaded();
    final DocNode operation = document.parts().operations().getFirst().node();

    final DocNode reference = OperationNodes.responses(operation).getFirst().value();
    assertThat(reference.reference()).contains("#/components/responses/Ok");
    assertThat(document.resolve(reference).string("description")).contains("ok");
    assertThat(YamlTrees.at(document.resolve(reference).anchor())).isEqualTo("10:5");
    assertThat(document.resolve(reference, View.RAW)).isSameAs(reference);
    assertThat(document.target(reference)).isPresent();
    assertThat(document.get(operation, "responses", View.RESOLVED)).isPresent();
  }

  @Test
  void followsAChainAcrossThreeFiles() {
    final Map<String, String> files =
        Map.of(
            "api/openapi.yaml",
            "openapi: 3.0.0\ncomponents:\n  schemas:\n    A:\n      $ref: 'schemas/a.yaml#/A'\n",
            "api/schemas/a.yaml",
            "A:\n  $ref: 'b/b.yaml'\n",
            "api/schemas/b/b.yaml",
            "type: object\nproperties:\n  id:\n    $ref: '../../common.yaml#/Id'\n",
            "api/common.yaml",
            "Id:\n  type: string\n");
    final LoadResult result = new FakeProject(files).load("api/openapi.yaml");
    assertThat(result.errors()).isEmpty();
    final OpenApiDocument document = result.document().orElseThrow();

    final DocNode a = document.parts().component("schemas").getFirst().value();
    final DocNode resolved = document.resolve(a);
    assertThat(resolved.file()).isEqualTo("api/schemas/b/b.yaml");
    final DocNode property = resolved.get("properties").orElseThrow().get("id").orElseThrow();
    final DocNode id = document.resolve(property);
    assertThat(id.file()).isEqualTo("api/common.yaml");
    assertThat(id.string("type")).contains("string");
    assertThat(document.files().keySet())
        .containsExactly(
            "api/common.yaml", "api/openapi.yaml", "api/schemas/a.yaml", "api/schemas/b/b.yaml");
  }

  @Test
  void aFileReferencedTwiceIsReadOnce() {
    final String spec =
        "openapi: 3.0.0\ncomponents:\n  schemas:\n    A:\n      $ref: 'x.yaml'\n    B:\n      $ref: 'x.yaml#/'\n";
    final FakeProject project = FakeProject.of(spec, "x.yaml", "type: string\n");

    project.loaded();

    assertThat(project.reads()).containsExactly("openapi.yaml", "x.yaml");
  }

  @Test
  void aCycleThroughPropertiesIsNotAnErrorAndAWalkEndsOnIt() {
    final String spec =
        """
        openapi: 3.0.0
        components:
          schemas:
            Node:
              type: object
              properties:
                next:
                  $ref: '#/components/schemas/Node'
        """;
    final OpenApiDocument document = FakeProject.of(spec).loaded();

    final List<DocNode> resolved = document.walk(View.RESOLVED).toList();
    assertThat(resolved).doesNotHaveDuplicates();
    assertThat(document.walk(View.RAW).filter(node -> node.reference().isPresent())).hasSize(1);
    assertThat(resolved.stream().filter(node -> node.reference().isPresent())).isEmpty();
  }

  @Test
  void aFragmentOfEscapedAndIndexedPointersIsFollowed() {
    final String spec =
        """
        openapi: 3.0.0
        paths:
          /a~b:
            get:
              tags: [x, y]
        components:
          schemas:
            A:
              $ref: '#/paths/~1a~0b/get/tags/1'
        """;
    final OpenApiDocument document = FakeProject.of(spec).loaded();

    final DocNode a = document.parts().component("schemas").getFirst().value();
    assertThat(document.resolve(a).text()).isEqualTo("y");
  }
}
