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
}
