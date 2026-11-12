package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OwaspSchemaRulesTest {

  private static final String HEAD =
      "openapi: 3.0.3\npaths:\n  /p:\n    post:\n      requestBody:\n        content:\n"
          + "          application/json:\n            schema:\n              $ref: '#/components/schemas/Req'\n"
          + "      responses:\n        '200':\n          description: d\n          content:\n"
          + "            application/json:\n              schema:\n                $ref: '#/components/schemas/Resp'\n"
          + "components:\n  schemas:\n";

  private static final String SCHEMAS =
      "    Req:\n      type: object\n      properties:\n        list:\n          type: array\n"
          + "        name:\n          type: string\n    Resp:\n      type: object\n      properties:\n"
          + "        list:\n          type: array\n        name:\n          type: string\n";

  private static List<String> positions(final String rule, final String schemas) {
    final String req = schemas.contains("Req:") ? "" : "    Req:\n      type: boolean\n";
    final String resp = schemas.contains("Resp:") ? "" : "    Resp:\n      type: boolean\n";
    return RuleFixtures.positions(rule, HEAD + req + schemas + resp);
  }

  @Test
  void limitsApplyToRequestSchemasOnly() {
    assertThat(positions("owasp-array-limit", SCHEMAS)).containsExactly("23:11");
    assertThat(positions("owasp-string-limit", SCHEMAS)).containsExactly("25:11");
  }

  @Test
  void restrictionsApplyToEverySchema() {
    assertThat(positions("owasp-string-restricted", SCHEMAS)).containsExactly("25:11", "32:11");
    assertThat(
            positions(
                "owasp-integer-format", "    Req:\n      type: integer\n      format: int32\n"))
        .isEmpty();
  }

  @Test
  void integerBoundsMustBeComplete() {
    for (final String bounds :
        List.of(
            "", "minimum: 1\n", "maximum: 1\n", "exclusiveMinimum: 1\n", "exclusiveMaximum: 1\n")) {
      assertThat(
              positions(
                  "owasp-integer-limit",
                  "    Req:\n      type: integer\n" + bounds.replaceAll("(?m)^", "      ")))
          .hasSize(1);
    }
    assertThat(
            positions(
                "owasp-integer-limit",
                "    Req:\n      type: integer\n      minimum: 1\n      exclusiveMaximum: 9\n"))
        .isEmpty();
  }

  @Test
  void objectsAreClosedOrTypedAndMapsAreBounded() {
    assertThat(positions("owasp-no-additional-properties", "    Req:\n      type: object\n"))
        .hasSize(1);
    assertThat(
            positions(
                "owasp-no-additional-properties",
                "    Req:\n      allOf:\n        - type: object\n    Resp:\n      type: object\n"))
        .isEmpty();
    assertThat(
            positions(
                "owasp-constrained-additional-properties",
                "    Req:\n      type: object\n      additionalProperties: true\n"))
        .hasSize(1);
    assertThat(
            positions(
                "owasp-constrained-additional-properties",
                "    Req:\n      type: object\n      additionalProperties: false\n"))
        .isEmpty();
  }
}
