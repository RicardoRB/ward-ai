package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicationRulesTest {

  private static List<String> messages(final String spec) {
    return RuleFixtures.run("description-duplication", spec).stream()
        .map(OpenApiFinding::message)
        .toList();
  }

  @Test
  void aRepeatedDescriptionPointsAtTheLaterOne() {
    assertThat(
            messages(
                "openapi: 3.0.3\ninfo:\n  description: same\ntags:\n  - name: a\n    description: same\n"))
        .containsExactly("Description at line `6` is a duplicate of line `3`");
  }

  @Test
  void aRepeatedSummaryAndASummaryThatRepeatsADescriptionAreReported() {
    final String spec =
        "openapi: 3.0.3\npaths:\n  /a:\n    summary: twice\n    get:\n      summary: twice\n      description: other\n"
            + "    put:\n      summary: other\n";
    assertThat(messages(spec))
        .containsExactly(
            "Summary at line `6` is a duplicate of line `4`",
            "Description at line `6` is a duplicate of line `4`",
            "Description at line `9` is a duplicate of line `7`");
  }

  @Test
  void distinctTextsAreFine() {
    assertThat(messages("openapi: 3.0.3\ninfo:\n  description: one\n  summary: two\n")).isEmpty();
  }
}
