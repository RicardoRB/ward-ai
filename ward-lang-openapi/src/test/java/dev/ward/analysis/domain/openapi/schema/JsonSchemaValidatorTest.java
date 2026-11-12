package dev.ward.analysis.domain.openapi.schema;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.YamlParser;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Dialect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

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

  private static List<String> messages(
      final Dialect dialect, final boolean all, final String schema, final String data) {
    return new JsonSchemaValidator(dialect, all, holder -> java.util.Optional.empty())
        .validate(node(schema), node(data)).stream().map(SchemaError::message).toList();
  }

  private static List<String> draft4(final String schema, final String data) {
    return messages(Dialect.DRAFT4, false, schema, data);
  }

  @Test
  void typesAndNullableAreChecked() {
    assertThat(draft4("{type: string}", "1")).containsExactly("must be string");
    assertThat(draft4("{type: [string, integer]}", "true"))
        .containsExactly("must be string,integer");
    assertThat(draft4("{type: string, nullable: true}", "null")).isEmpty();
    assertThat(draft4("{type: string, nullable: true}", "1"))
        .containsExactly("must be string,null");
    assertThat(draft4("{type: integer}", "1.0")).isEmpty();
    assertThat(draft4("{type: number}", "1.5")).isEmpty();
    assertThat(draft4("{type: boolean}", "true")).isEmpty();
    assertThat(draft4("{type: array}", "[]")).isEmpty();
    assertThat(draft4("{type: object}", "{}")).isEmpty();
    assertThat(draft4("{type: bogus}", "{}")).containsExactly("must be bogus");
  }

  @Test
  void numbersStringsAndFormatsAreChecked() {
    assertThat(draft4("{minimum: 5}", "1")).containsExactly("must be >= 5");
    assertThat(draft4("{maximum: 5, exclusiveMaximum: true}", "5")).containsExactly("must be < 5");
    assertThat(draft4("{minimum: 5, exclusiveMinimum: true}", "5")).containsExactly("must be > 5");
    assertThat(draft4("{maximum: 5}", "6")).containsExactly("must be <= 5");
    assertThat(draft4("{multipleOf: 3}", "4")).containsExactly("must be multiple of 3");
    assertThat(draft4("{multipleOf: 3}", "6")).isEmpty();
    assertThat(draft4("{minLength: 3}", "ab"))
        .containsExactly("must NOT have fewer than 3 characters");
    assertThat(draft4("{maxLength: 1}", "ab"))
        .containsExactly("must NOT have more than 1 characters");
    assertThat(draft4("{pattern: '^a'}", "b")).containsExactly("must match pattern \"^a\"");
    assertThat(draft4("{pattern: '('}", "b")).isEmpty();
    assertThat(draft4("{format: uuid}", "x")).containsExactly("must match format \"uuid\"");
    assertThat(draft4("{format: unknown}", "x")).isEmpty();
    for (final String[] pair :
        new String[][] {
          {"date", "2020-02-30"},
          {"time", "10:00:00"},
          {"date-time", "2020-01-01T10:00:00"},
          {"uri", "nope"},
          {"uri-reference", "a b"},
          {"email", "x"},
          {"hostname", "a b"},
          {"ipv4", "300.1.1." + "1"},
          {"ipv6", "zz"},
          {"byte", "***"},
          {"json-pointer", "a"},
          {"regex", "("}
        }) {
      assertThat(draft4("{format: " + pair[0] + "}", "\"" + pair[1] + "\"")).as(pair[0]).hasSize(1);
    }
    assertThat(draft4("{format: date}", "2020-02-29")).isEmpty();
    assertThat(draft4("{format: date-time}", "2020-01-01T10:00:00Z")).isEmpty();
    assertThat(draft4("{format: uri}", "https://a.example/b?c#d")).isEmpty();
    assertThat(draft4("{format: email}", "a@b.example")).isEmpty();
    assertThat(draft4("{format: ipv6}", ":" + ":1")).isEmpty();
  }

  @Test
  void arraysAndObjectsAreChecked() {
    assertThat(draft4("{minItems: 2}", "[1]")).containsExactly("must NOT have fewer than 2 items");
    assertThat(draft4("{maxItems: 1}", "[1, 2]"))
        .containsExactly("must NOT have more than 1 items");
    assertThat(draft4("{uniqueItems: true}", "[1, 2, 1]"))
        .containsExactly("must NOT have duplicate items (items ## 0 and 2 are identical)");
    assertThat(draft4("{uniqueItems: true, items: {type: string}}", "[a, b, a]"))
        .containsExactly("must NOT have duplicate items (items ## 2 and 0 are identical)");
    assertThat(draft4("{items: {type: string}}", "[a, 1]")).containsExactly("must be string");
    assertThat(
            draft4(
                "{items: [{type: string}, {type: integer}], additionalItems: {type: boolean}}",
                "[a, 1, 2]"))
        .containsExactly("must be boolean");
    assertThat(draft4("{minProperties: 2}", "{a: 1}"))
        .containsExactly("must NOT have fewer than 2 properties");
    assertThat(draft4("{maxProperties: 1}", "{a: 1, b: 2}"))
        .containsExactly("must NOT have more than 1 properties");
    assertThat(draft4("{required: [a]}", "{}")).containsExactly("must have required property 'a'");
    assertThat(
            draft4(
                "{properties: {a: {type: string}}, additionalProperties: false}", "{a: 1, b: 2}"))
        .containsExactly("must NOT have additional properties");
    assertThat(draft4("{additionalProperties: {type: integer}}", "{a: x}"))
        .containsExactly("must be integer");
    assertThat(
            draft4(
                "{patternProperties: {'^x-': {type: string}}, additionalProperties: false}",
                "{x-a: 1}"))
        .containsExactly("must be string");
  }

  @Test
  void combinatorsAreChecked() {
    assertThat(draft4("{const: 1}", "2")).containsExactly("must be equal to constant");
    assertThat(draft4("{enum: [a, b]}", "c"))
        .containsExactly("must be equal to one of the allowed values");
    assertThat(draft4("{not: {type: string}}", "x")).containsExactly("must NOT be valid");
    assertThat(draft4("{anyOf: [{type: string}, {type: integer}]}", "true"))
        .containsExactly("must be string", "must be integer", "must match a schema in anyOf");
    assertThat(draft4("{oneOf: [{type: string}, {type: string}]}", "a"))
        .containsExactly("must match exactly one schema in oneOf");
    assertThat(draft4("{oneOf: [{type: string}, {type: integer}]}", "1")).isEmpty();
    assertThat(draft4("{allOf: [{required: [a]}, {required: [b]}]}", "{}"))
        .containsExactly("must have required property 'a'");
    assertThat(messages(Dialect.DRAFT4, true, "{allOf: [{required: [a]}, {required: [b]}]}", "{}"))
        .containsExactly("must have required property 'a'", "must have required property 'b'");
    assertThat(
            messages(
                Dialect.DRAFT2020,
                false,
                "{if: {required: [t]}, then: {required: [y]}, else: {required: [z]}}",
                "{t: 1}"))
        .containsExactly("must have required property 'y'", "must match \"then\" schema");
    assertThat(
            messages(
                Dialect.DRAFT2020,
                false,
                "{if: {required: [t]}, then: {required: [y]}, else: {required: [z]}}",
                "{}"))
        .containsExactly("must have required property 'z'", "must match \"else\" schema");
  }

  @Test
  void draft2020ReadsTuplesBoundsAndUnevaluatedProperties() {
    assertThat(
            messages(
                Dialect.DRAFT2020,
                false,
                "{prefixItems: [{type: string}], items: {type: boolean}}",
                "[1, x]"))
        .containsExactly("must be string");
    assertThat(
            messages(Dialect.DRAFT2020, false, "{exclusiveMinimum: 5, exclusiveMaximum: 6}", "5"))
        .containsExactly("must be > 5");
    assertThat(messages(Dialect.DRAFT2020, false, "{exclusiveMaximum: 6}", "6"))
        .containsExactly("must be < 6");
    assertThat(
            messages(
                Dialect.DRAFT2020,
                false,
                "{properties: {a: {}}, unevaluatedProperties: false}",
                "{a: 1, b: 2}"))
        .containsExactly("must NOT have unevaluated properties");
    assertThat(
            messages(
                Dialect.DRAFT2020, false, "{unevaluatedProperties: {type: integer}}", "{b: x}"))
        .containsExactly("must be integer");
    assertThat(messages(Dialect.DRAFT2020, false, "false", "1"))
        .containsExactly("boolean schema is false");
    assertThat(messages(Dialect.DRAFT2020, false, "true", "1")).isEmpty();
  }

  @Test
  void readOnlyAndWriteOnlyPropertiesCanBeLeftOut() {
    final String schema =
        "{required: [a, b], properties: {a: {readOnly: true}, b: {writeOnly: true}}}";
    final DocNode data = node("{}");
    final JsonSchemaValidator request =
        new JsonSchemaValidator(Dialect.DRAFT4, true, holder -> java.util.Optional.empty())
            .relaxing(JsonSchemaValidator.Relax.READ_ONLY);
    assertThat(request.validate(node(schema), data))
        .extracting(SchemaError::detail)
        .containsExactly("b");
    final JsonSchemaValidator response =
        new JsonSchemaValidator(Dialect.DRAFT4, true, holder -> java.util.Optional.empty())
            .relaxing(JsonSchemaValidator.Relax.WRITE_ONLY);
    assertThat(response.validate(node(schema), data))
        .extracting(SchemaError::detail)
        .containsExactly("a");
  }
}
