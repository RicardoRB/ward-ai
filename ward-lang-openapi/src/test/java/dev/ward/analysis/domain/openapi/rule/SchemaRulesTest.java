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
