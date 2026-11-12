package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathParameterRulesTest {

  private static List<String> messages(final String paths) {
    return RuleFixtures.run("path-params", "openapi: 3.0.3\npaths:\n" + paths).stream()
        .map(OpenApiFinding::message)
        .toList();
  }

  @Test
  void aPathParameterMustBeDeclaredAtThePathItemOrInEveryOperation() {
    assertThat(
            messages(
                "  /a/{id}:\n    get: {}\n    put:\n      parameters:\n        - {name: id, in: path, required: true}\n"))
        .containsExactly(
            "parameter named `id` must be defined as part of the path `/a/{id}` definition, or in the `GET` operation(s)");
    assertThat(
            messages(
                "  /a/{id}:\n    parameters:\n      - {name: id, in: path, required: true}\n    get: {}\n"))
        .isEmpty();
  }

  @Test
  void aParameterNamedInThePathTwiceIsReported() {
    assertThat(
            messages(
                "  /a/{x}/{x}:\n    parameters:\n      - {name: x, in: path, required: true}\n    get: {}\n"))
        .containsExactly("path `/a/{x}/{x}` must not use the parameter `x` multiple times");
  }

  @Test
  void declaredParametersMustBeRequiredUniqueAndInThePath() {
    final String spec =
        "  /a/{x}:\n    parameters:\n      - {name: x, in: path, required: false}\n      - {name: x, in: path, required: true}\n"
            + "      - {name: gone, in: path, required: true}\n    get:\n      parameters:\n"
            + "        - {name: x, in: path, required: false}\n        - {name: x, in: path}\n        - {name: nope, in: path}\n        - {name: q, in: query}\n";
    assertThat(messages(spec))
        .containsExactly(
            "path parameter named `x` at `/a/{x}` must have `required` set to `true`",
            "path parameter named `x` at `/a/{x}` is a duplicate of another parameter with the same name",
            "path parameter named `gone` does not exist in path `/a/{x}`",
            "`/a/{x}` `GET` parameter named `x` must have `required` set to `true`",
            "`GET` parameter named `x` at `/a/{x}` is a duplicate of another parameter with the same name",
            "`GET` parameter named `nope` does not exist in path `/a/{x}`");
  }

  @Test
  void equivalentPathsOfTheSameMethodAreReportedOnTheLaterOne() {
    assertThat(
            messages(
                "  /a/{x}:\n    parameters:\n      - {name: x, in: path, required: true}\n    get: {}\n"
                    + "  /a/{y}:\n    parameters:\n      - {name: y, in: path, required: true}\n    get: {}\n"
                    + "  /a/{z}:\n    parameters:\n      - {name: z, in: path, required: true}\n    post: {}\n"))
        .containsExactly(
            "GET: paths `/a/{x}` and `/a/{y}` must not be equivalent, paths must be unique for the same HTTP method");
  }
}
