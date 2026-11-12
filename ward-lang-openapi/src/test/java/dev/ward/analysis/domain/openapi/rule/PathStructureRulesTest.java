package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathStructureRulesTest {

  private static final String HEAD = "openapi: 3.0.3\npaths:\n";

  private static List<String> messages(final String rule, final String paths) {
    return RuleFixtures.run(rule, HEAD + paths).stream().map(OpenApiFinding::message).toList();
  }

  @Test
  void pathsAreAmbiguousWhenTheirShapesAndMethodsMatch() {
    final String same = "    get: {}\n";
    assertThat(messages("no-ambiguous-paths", "  /a/{id}:\n" + same + "  /a/{name}:\n" + same))
        .containsExactly(
            "paths are ambiguous with one another: `/a/{id}` (GET) and `/a/{name}` (GET)");
    assertThat(
            messages("no-ambiguous-paths", "  /a/{id}:\n" + same + "  /a/{name}:\n    post: {}\n"))
        .isEmpty();
    assertThat(messages("no-ambiguous-paths", "  /a/{id}:\n" + same + "  /a/mine:\n" + same))
        .isEmpty();
    assertThat(messages("no-ambiguous-paths", "  /a/{id}:\n" + same + "  /a/{+rest}:\n" + same))
        .isEmpty();
    assertThat(
            messages(
                "no-ambiguous-paths",
                "  /a/{id}:\n    parameters:\n      - {name: id, in: path, schema: {type: integer}}\n"
                    + same
                    + "  /a/{n}:\n    parameters:\n      - {name: n, in: path, schema: {type: string}}\n"
                    + same))
        .isEmpty();
  }

  @Test
  void parametersNeedAnInAndMustNotRepeat() {
    final String spec =
        "  /a:\n    get:\n      parameters:\n        - {name: x, in: query}\n        - {name: x, in: query}\n"
            + "        - {name: y}\n        - {name: b1, in: body}\n        - {name: b2, in: body}\n"
            + "        - {name: f, in: formData}\n        - $ref: '#/components/parameters/P'\n"
            + "        - $ref: '#/components/parameters/P'\ncomponents:\n  parameters:\n    P:\n      name: p\n      in: header\n";
    assertThat(messages("operation-parameters", spec))
        .containsExactly(
            "the `GET` operation parameter at path `/a`, index 1 has a duplicate name `x` and `in` type",
            "the `GET` operation at path `/a` contains a parameter with no `in` value",
            "the `GET` operation at path `/a` contains a duplicate param in:body definition",
            "the `GET` operation at path `/a` contains parameters using both in:body and in:formData",
            "the `GET` operation parameter at path `/a`, index 7 has a duplicate ref `#/components/parameters/P`");
  }

  @Test
  void anOperationThatIsAReferenceIsReported() {
    assertThat(
            messages(
                "path-item-refs", "  /a:\n    get: {}\n    put:\n      $ref: '#/paths/~1a/get'\n"))
        .containsExactly(
            "`PUT` operation at path `/a` uses a $ref, which is not valid; $ref is only allowed on the path item itself");
  }
}
