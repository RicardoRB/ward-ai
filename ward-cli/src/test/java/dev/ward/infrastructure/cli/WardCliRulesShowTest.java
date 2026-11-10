package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward rules show}: one rule as JSON, and the closest id for an unknown one. */
@DisabledOnOs(OS.WINDOWS)
class WardCliRulesShowTest extends WardCliIntegrationSupport {
  private static final String RULES = "rules";
  private static final String SHOW = "show";
  private static final String METHOD = "ward:java/method-length";

  @Test
  void showGivesJsonWithOptions() {
    assertThat(run(RULES, SHOW, METHOD, "--format", "json")).isEqualTo(ExitCodes.OK);

    final JsonTree rule = JsonTree.parse(out.toString());
    assertThat(rule.get("id").asString()).isEqualTo(METHOD);
    assertThat(rule.get("options").size()).isPositive();
  }

  @Test
  void showAnUnknownIdSuggestsTheClosestOne() {
    assertThat(run(RULES, SHOW, "ward:java/method-lenght")).isEqualTo(ExitCodes.ERROR);

    assertThat(err.toString()).contains("did you mean \"" + METHOD + "\"");
  }
}
