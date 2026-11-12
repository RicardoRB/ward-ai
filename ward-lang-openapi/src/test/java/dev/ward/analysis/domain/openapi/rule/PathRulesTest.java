package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathRulesTest {

  private static final int PATHS_CAPACITY = 200;

  private static String paths(final String... keys) {
    final StringBuilder spec = new StringBuilder(PATHS_CAPACITY).append("openapi: 3.0.3\npaths:\n");
    for (final String key : keys) {
      spec.append("  ").append(key).append(":\n    get:\n      responses: {}\n");
    }
    return spec.toString();
  }

  @Test
  void patternRulesReportAtThePathKey() {
    assertThat(RuleFixtures.positions("path-declarations-must-exist", paths("/a/{}", "/b")))
        .containsExactly("3:3");
    assertThat(RuleFixtures.positions("path-keys-no-trailing-slash", paths("/a/", "/")))
        .containsExactly("3:3");
    assertThat(RuleFixtures.positions("path-not-include-query", paths("'/a?x'", "/b")))
        .containsExactly("3:3");
  }
}
