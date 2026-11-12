package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiModelTest {

  private static final String SPEC =
      """
      openapi: 3.0.3
      info:
        title: Pets
        version: "1"
      servers:
        - url: https://api.example.com
      tags:
        - name: pets
      paths:
        /pets:
          get:
            operationId: listPets
            parameters:
              - name: limit
                in: query
            responses:
              "200":
                description: ok
                headers:
                  X-Rate:
                    description: rate
          post:
            requestBody:
              content: {}
            responses: {}
      components:
        schemas:
          Pet:
            type: object
      """;

  @Test
  void readsTheStandardPartsWithTheirPositions() {
    final OpenApiDocument document = FakeProject.of(SPEC).loaded();
    final OpenApiParts parts = document.parts();

    assertThat(document.version()).isEqualTo("3.0.3");
    assertThat(document.isVersion31()).isFalse();
    assertThat(parts.info().orElseThrow().string("title")).contains("Pets");
    assertThat(YamlTrees.at(parts.info().orElseThrow().span())).isEqualTo("3:3");
    assertThat(YamlTrees.at(parts.info().orElseThrow().keySpan().orElseThrow())).isEqualTo("2:1");
    assertThat(parts.servers()).hasSize(1);
    assertThat(parts.tags()).hasSize(1);
    assertThat(parts.paths()).extracting(DocNode.Entry::key).containsExactly("/pets");
    assertThat(parts.component("schemas")).extracting(DocNode.Entry::key).containsExactly("Pet");
    assertThat(YamlTrees.at(parts.component("schemas").getFirst().keySpan())).isEqualTo("28:5");
  }

  @Test
  void readsOperationsParametersResponsesAndHeaders() {
    final OpenApiDocument document = FakeProject.of(SPEC).loaded();
    final List<Operation> operations = document.parts().operations();

    assertThat(operations).extracting(Operation::method).containsExactly("get", "post");
    final Operation get = operations.getFirst();
    assertThat(get.node().file()).isEqualTo("openapi.yaml");
    assertThat(get.node().pointer()).isEqualTo("/paths/~1pets/get");
    assertThat(YamlTrees.at(get.node().anchor())).isEqualTo("11:5");
    assertThat(OperationNodes.parameters(get.node())).hasSize(1);
    assertThat(OperationNodes.parameters(get.node()).getFirst().string("name")).contains("limit");
    assertThat(OperationNodes.responses(get.node()))
        .extracting(DocNode.Entry::key)
        .containsExactly("200");
    final DocNode ok = OperationNodes.responses(get.node()).getFirst().value();
    assertThat(OperationNodes.headers(ok)).extracting(DocNode.Entry::key).containsExactly("X-Rate");
    assertThat(OperationNodes.requestBody(operations.getLast().node())).isPresent();
  }

  @Test
  void aMissingPartIsEmptyNeverNull() {
    final OpenApiDocument document = FakeProject.of("openapi: 3.1.0\n").loaded();
    final OpenApiParts parts = document.parts();

    assertThat(document.isVersion31()).isTrue();
    assertThat(parts.info()).isEmpty();
    assertThat(parts.servers()).isEmpty();
    assertThat(parts.tags()).isEmpty();
    assertThat(parts.security()).isEmpty();
    assertThat(parts.paths()).isEmpty();
    assertThat(parts.operations()).isEmpty();
    assertThat(parts.components()).isEmpty();
    assertThat(parts.component("schemas")).isEmpty();
    assertThat(document.root().get("nothing")).isEmpty();
    assertThat(document.root().at(0)).isEmpty();
    assertThat(OperationNodes.parameters(document.root())).isEmpty();
    assertThat(OperationNodes.responses(document.root())).isEmpty();
    assertThat(OperationNodes.headers(document.root())).isEmpty();
    assertThat(OperationNodes.requestBody(document.root())).isEmpty();
  }

  @Test
  void scalarsAndCollectionsAreTold() {
    final DocNode root = FakeProject.of("openapi: 3.0.0\ntags: [a]\n").loaded().root();

    assertThat(root.get("openapi").orElseThrow().isScalar()).isTrue();
    assertThat(root.get("tags").orElseThrow().isScalar()).isFalse();
    assertThat(root.children()).hasSize(2);
    assertThat(root.get("tags").orElseThrow().children()).hasSize(1);
    assertThat(root.get("tags").orElseThrow().kind()).isEqualTo(DocNode.Kind.ARRAY);
  }

  @Test
  void jsonAndYamlDocumentsGiveTheSameModel() {
    final OpenApiDocument yaml = FakeProject.of("openapi: 3.0.0\ninfo:\n  title: T\n").loaded();
    final OpenApiDocument json =
        FakeProject.of("{\"openapi\": \"3.0.0\", \"info\": {\"title\": \"T\"}}").loaded();

    assertThat(json.parts().info().orElseThrow().string("title"))
        .isEqualTo(yaml.parts().info().orElseThrow().string("title"));
    assertThat(json.walk(View.RAW).map(DocNode::pointer).toList())
        .isEqualTo(yaml.walk(View.RAW).map(DocNode::pointer).toList());
  }
}
