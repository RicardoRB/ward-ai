package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DescriptionRulesTest {

  private static final String HEAD = "openapi: 3.0.3\ninfo:\n  title: T\n";

  @Test
  void operationsNeedADescriptionOrSummaryAndTheirBodiesAndResponsesNeedOne() {
    final String spec =
        HEAD
            + "paths:\n  /a:\n    post:\n      requestBody:\n        content: {}\n"
            + "      responses:\n        '201':\n          content: {}\n";
    assertThat(RuleFixtures.run("operation-description", spec))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "operation method `POST` at path `/a` is missing a description or summary",
            "operation method `POST` `requestBody` at path `/a` is missing a `description`",
            "operation method `POST` response code `201` `responseBody` at path `/a` is missing a `description`");
    assertThat(
            RuleFixtures.run(
                "operation-description",
                HEAD
                    + "paths:\n  /a:\n    get:\n      summary: s\n      responses:\n        '200':\n          description: d\n"))
        .isEmpty();
  }

  @Test
  void parametersAreCheckedInComponentsPathItemsAndOperations() {
    final String spec =
        HEAD
            + "paths:\n  /a:\n    parameters:\n      - in: query\n    get:\n      parameters:\n"
            + "        - name: n\n          in: query\n          description: ok\n        - name: m\n          in: query\n"
            + "      responses: {}\ncomponents:\n  parameters:\n    P:\n      name: p\n";
    assertThat(RuleFixtures.run("oas3-parameter-description", spec))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "the parameter `P` does not contain a description",
            "the parameter `parameter[0]` does not contain a description",
            "the parameter `m` does not contain a description");
  }
}
