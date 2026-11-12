package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchemaRulesTest {

  private static final String HEAD = "openapi: 3.0.3\npaths: {}\ncomponents:\n  schemas:\n";

  private static String messages(final String rule, final String schemas) {
    return RuleFixtures.run(rule, HEAD + schemas).stream()
        .map(OpenApiFinding::message)
        .reduce((left, right) -> left + " | " + right)
        .orElse("");
  }

  @Test
  void enumRulesCheckDuplicatesTypesAndNullability() {
    assertThat(messages("duplicated-entry-in-enum", "    A:\n      enum: [a, b, a, a]\n"))
        .isEqualTo("enum contains a duplicate: `a`");
    assertThat(messages("typed-enum", "    A:\n      type: boolean\n      enum: [true, x]\n"))
        .isEqualTo(
            "enum type mismatch: enum value 'x' is a string, but it's defined as a 'boolean'");
    assertThat(messages("typed-enum", "    A:\n      type: [string, 'null']\n      enum: [a, 1]\n"))
        .contains("'1' is a integer");
    assertThat(messages("typed-enum", "    A:\n      type: number\n      enum: [1, 2.5]\n"))
        .isEmpty();
    assertThat(
            messages(
                "nullable-enum-contains-null",
                "    A:\n      type: [string, 'null']\n      enum: [a]\n"))
        .startsWith("enum is defined as nullable");
    assertThat(
            messages(
                "nullable-enum-contains-null",
                "    A:\n      nullable: true\n      enum: [a, null]\n"))
        .isEmpty();
  }

  @Test
  void propertyNamesAreClassifiedByTheirCase() {
    assertThat(
            messages(
                "camel-case-properties",
                "    A:\n      properties:\n        Pascal: {type: string}\n        kebab-case: {type: string}\n"
                    + "        snake_case: {type: string}\n        SCREAM_CASE: {type: string}\n"
                    + "        Odd_Name: {type: string}\n        fine: {type: string}\n"))
        .isEqualTo(
            "property `Pascal` is `PascalCase` not `camelCase` | property `kebab-case` is"
                + " `kebab-case` not `camelCase` | property `snake_case` is `snake_case` not"
                + " `camelCase` | property `SCREAM_CASE` is `SCREAMING_SNAKE_CASE` not `camelCase` |"
                + " property 'Odd_Name' is using an unclassified case, not camelCase");
  }

  @Test
  void requiredFieldsMustBeDefinedAsProperties() {
    assertThat(messages("required-fields-defined", "    A:\n      required: [x]\n"))
        .isEqualTo("object contains `required` fields but no `properties`");
    assertThat(
            messages(
                "required-fields-defined",
                "    A:\n      required: [x, y]\n      properties:\n        x: {type: string}\n"))
        .isEqualTo("`required` field `y` is not defined in `properties`");
  }

  @Test
  void missingTypeSkipsSchemasThatDescribeTheirShapeAnotherWay() {
    assertThat(messages("oas-missing-type", "    A:\n      description: d\n"))
        .isEqualTo("schema is missing a `type` field");
    assertThat(
            messages(
                "oas-missing-type",
                "    A:\n      type: object\n      properties:\n        p:\n          description: d\n"
                    + "        q:\n          enum: [a]\n"))
        .contains("schema property: `p` is missing a `type` field");
  }

  @Test
  void aSingleItemCombinatorIsKeptOnlyWhenARefCarriesSiblingsInOpenApi30() {
    assertThat(
            messages("no-unnecessary-combinator", "    A:\n      anyOf:\n        - type: string\n"))
        .contains("`anyOf` combinator containing only one item");
    assertThat(
            messages(
                "no-unnecessary-combinator",
                "    B:\n      type: string\n    A:\n      description: d\n      allOf:\n        - $ref: '#/components/schemas/B'\n"))
        .isEmpty();
  }

  @Test
  void allOfBranchesMustShareATypeForAProperty() {
    assertThat(
            messages(
                "allof-conflicts",
                "    A:\n      allOf:\n        - properties: {v: {type: string}}\n        - properties: {v: {type: integer}}\n"))
        .contains("`allOf` property `v` declared as [string, integer]");
    assertThat(
            messages(
                "allof-conflicts",
                "    A:\n      allOf:\n        - properties: {v: {type: number}}\n        - properties: {v: {type: [integer, 'null']}}\n"))
        .isEmpty();
  }

  @Test
  void componentsNoReferenceReachesAreReported() {
    final String spec =
        "openapi: 3.0.3\nparts: 1\ncomponents:\n  schemas:\n    A:\n      type: string\n    B:\n      $ref: '#/components/schemas/A'\n";
    assertThat(RuleFixtures.run("oas3-unused-component", spec))
        .extracting(OpenApiFinding::message)
        .containsExactly("`#/components/schemas/B` is potentially unused or has been orphaned");
  }
}
