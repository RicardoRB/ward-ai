package dev.ward.analysis.domain.openapi.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.YamlParser;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Dialect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaSupportTest {

  private static DocNode node(final String text) {
    return OpenApiDocuments.fromTrees(
            "a.yaml",
            Map.of("a.yaml", YamlParser.parse("openapi: 3.0.3\nx: " + text + "\n").tree()))
        .document()
        .orElseThrow()
        .root()
        .get("x")
        .orElseThrow();
  }

  @Test
  void aReferenceIsFollowedOrFails() {
    final OpenApiDocument document =
        OpenApiDocuments.fromTrees(
                "a.yaml",
                Map.of(
                    "a.yaml",
                    YamlParser.parse("openapi: 3.0.3\ny: {type: string}\nx: {$ref: '#/y'}\n")
                        .tree()))
            .document()
            .orElseThrow();
    final DocNode schema = document.root().get("x").orElseThrow();
    assertThat(
            new JsonSchemaValidator(
                    Dialect.DRAFT4, false, holder -> document.target(holder).map(document::resolve))
                .validate(schema, node("1")))
        .hasSize(1);
    assertThatThrownBy(
            () ->
                new JsonSchemaValidator(Dialect.DRAFT4, false, holder -> java.util.Optional.empty())
                    .validate(schema, node("1")))
        .isInstanceOf(JsonSchemaValidator.UnresolvedReferenceException.class);
  }

  @Test
  void messagesAreWordedLikeSpectral() {
    final DocNode data = node("{id: x, color: gren, tags: [1], extra: 1}");
    final DocNode schema =
        node(
            "{type: object, required: [name], additionalProperties: false, properties: {id: {type: integer}, color: {enum: [green, red]}, tags: {type: array, items: {type: string}}}}");
    final JsonSchemaValidator all =
        new JsonSchemaValidator(Dialect.DRAFT4, true, holder -> java.util.Optional.empty());
    final List<String> texts =
        SchemaMessages.format(all.validate(schema, data), data, List.of("example")).stream()
            .map(SchemaMessages.Message::text)
            .toList();
    assertThat(texts)
        .contains(
            "\"example\" property must have required property \"name\"",
            "Property \"extra\" is not expected to be here",
            "\"id\" property type must be integer",
            "Item at index 0 must be string");
    final DocNode color = node("{color: gren}");
    assertThat(
            SchemaMessages.format(
                    all.validate(node("{properties: {color: {enum: [green, red]}}}"), color),
                    color,
                    List.of("example"))
                .getFirst()
                .text())
        .isEqualTo(
            "\"color\" property must be equal to one of the allowed values: \"green\", \"red\". Did you mean \"green\"?");
    final DocNode scalar = node("5");
    assertThat(
            SchemaMessages.format(
                    new JsonSchemaValidator(
                            Dialect.DRAFT4, false, holder -> java.util.Optional.empty())
                        .validate(node("{type: string}"), scalar),
                    scalar,
                    List.of())
                .getFirst()
                .text())
        .isEqualTo("Value type must be string");
    final DocNode union = node("true");
    assertThat(
            SchemaMessages.format(
                    new JsonSchemaValidator(
                            Dialect.DRAFT4, false, holder -> java.util.Optional.empty())
                        .validate(node("{oneOf: [{type: string}, {type: integer}]}"), union),
                    union,
                    List.of("example"))
                .stream()
                .map(SchemaMessages.Message::text))
        .contains(
            "Boolean must match exactly one schema in oneOf. None of the schemas matched, see the other errors reported for this value"
                .replace("Boolean", "\"example\" property"));
  }

  @Test
  void pointersReadNestedValues() {
    final DocNode data = node("{a: [{b: 1}]}");
    assertThat(Pointers.at(data, "/a/0/b")).isPresent();
    assertThat(Pointers.at(data, "")).isPresent();
    assertThat(Pointers.at(data, "/a/3")).isEmpty();
    assertThat(JsonValues.equal(node("{a: 1, b: [1.0]}"), node("{b: [1], a: 1.0}"))).isTrue();
    assertThat(JsonValues.equal(node("[1]"), node("[1, 2]"))).isFalse();
  }
}
