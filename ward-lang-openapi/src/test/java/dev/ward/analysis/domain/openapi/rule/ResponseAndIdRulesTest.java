package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResponseAndIdRulesTest {

  private static final String HEAD = "openapi: 3.0.3\npaths:\n  /a:\n";

  @Test
  void successResponseWantsA2xxOr3xxAndAStringCode() {
    final String spec =
        HEAD
            + "    get:\n      operationId: g\n      responses:\n        default:\n          description: d\n"
            + "    post:\n      responses:\n        200:\n          description: d\n"
            + "    put:\n      responses:\n        '302':\n          description: d\n";
    assertThat(RuleFixtures.run("operation-success-response", spec))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "operation `g` must define at least a single `2xx` or `3xx` response",
            "operation `undefined operation (no operationId)` uses an `integer` instead of a"
                + " `string` for response code `200`");
  }

  @Test
  void operationIdsMustBeUniqueAndUrlFriendly() {
    final String spec =
        HEAD
            + "    get:\n      operationId: dup\n    post:\n      operationId: dup\n"
            + "    put:\n      operationId: 'bad id'\n";
    assertThat(RuleFixtures.run("operation-operation-id-unique", spec))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "the 'post' operation at path '/a' contains a duplicate operationId 'dup'");
    assertThat(RuleFixtures.run("operation-operation-id-valid-in-url", spec))
        .extracting(OpenApiFinding::message)
        .singleElement()
        .asString()
        .startsWith("OperationId must use URL friendly characters: `bad id`");
  }
}
