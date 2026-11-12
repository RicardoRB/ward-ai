package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpecificityRulesTest {

  private static List<String> messages(final String paths) {
    return RuleFixtures.run("paths-specificity-order", "openapi: 3.0.3\npaths:\n" + paths).stream()
        .map(OpenApiFinding::message)
        .toList();
  }

  @Test
  void aStaticPathAfterATemplatedOneIsReported() {
    assertThat(messages("  /a/{id}:\n    get: {}\n    post: {}\n  /a/mine:\n    get: {}\n"))
        .containsExactly(
            "path `/a/mine` should be declared before `/a/{id}` for method(s) GET because a static"
                + " segment at the first differing position is more specific than a templated"
                + " segment");
  }

  @Test
  void orderMethodsLengthsAndTypesDecideWhetherItMatters() {
    assertThat(messages("  /a/mine:\n    get: {}\n  /a/{id}:\n    get: {}\n")).isEmpty();
    assertThat(messages("  /a/{id}:\n    post: {}\n  /a/mine:\n    get: {}\n")).isEmpty();
    assertThat(messages("  /a/{id}:\n    get: {}\n  /a/mine/x:\n    get: {}\n")).isEmpty();
    assertThat(
            messages(
                "  /a/{id}:\n    parameters:\n      - {name: id, in: path, schema: {type: integer}}\n    get: {}\n"
                    + "  /a/mine:\n    get: {}\n"))
        .isEmpty();
    assertThat(
            messages(
                "  /a/{id}:\n    parameters:\n      - {name: id, in: path, schema: {type: integer}}\n    get: {}\n"
                    + "  /a/12:\n    get: {}\n"))
        .hasSize(1);
  }

  @Test
  void numbersAndBooleansAreMatchedByTheirLiterals() {
    final String template =
        "  /a/{v}:\n    parameters:\n      - {name: v, in: path, schema: {type: %s}}\n    get: {}\n  /a/%s:\n    get: {}\n";
    assertThat(messages(template.formatted("number", "1.5"))).hasSize(1);
    assertThat(messages(template.formatted("number", "x"))).isEmpty();
    assertThat(messages(template.formatted("boolean", "true"))).hasSize(1);
    assertThat(messages(template.formatted("boolean", "maybe"))).isEmpty();
  }
}
