package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownAndSecurityRulesTest {

  private static final String SPEC =
      "openapi: 3.0.3\ninfo:\n  description: 'run eval(x) <script>'\n"
          + "paths:\n  /p:\n    get:\n      responses:\n        '200':\n          description: ok\n"
          + "          content:\n            application/json:\n              examples:\n"
          + "                e:\n                  description: eval(skipped)\n"
          + "x-ext:\n  description: eval(y)\n";

  @Test
  void descriptionsAreScannedEverywhereButInExamples() {
    assertThat(RuleFixtures.positions("no-eval-in-markdown", SPEC))
        .containsExactly("3:16", "16:16");
    assertThat(RuleFixtures.run("no-script-tags-in-markdown", SPEC))
        .extracting(OpenApiFinding::message)
        .containsExactly("description contains content with `<script`, forbidden");
  }

  @Test
  void securityRequirementsMustNameADefinedScheme() {
    final String spec =
        "openapi: 3.0.3\npaths:\n  /p:\n    get:\n      security:\n        - nope: []\n"
            + "      responses: {}\ncomponents:\n  securitySchemes:\n    key:\n      type: apiKey\n"
            + "security:\n  - key: []\n  - other: []\n";
    assertThat(RuleFixtures.run("oas3-operation-security-defined", spec))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "Security definition points a non-existent securityScheme `nope`",
            "Security definition points a non-existent securityScheme `other`");
  }
}
