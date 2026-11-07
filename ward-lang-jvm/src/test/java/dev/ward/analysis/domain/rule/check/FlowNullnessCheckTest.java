package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowNullnessCheckTest {

  private static final int RULES = 16;
  private static final String DEREF = "np-always-null";
  private static final String SOME = "np-null-on-some-path";

  private static List<String> run(final String rule, final String parameters, final String body) {
    return FlowNullSources.run(rule, FlowNullSources.method(parameters, body));
  }

  @Test
  void namesCoverEverySpotBugsRuleOfTheChange() {
    assertThat(FlowNullnessCheck.names()).hasSize(RULES).contains(DEREF, SOME);
  }

  @Test
  void anUnknownRuleNameIsRejected() {
    assertThatThrownBy(() -> FlowNullnessCheck.of("nope", "m"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theCheckNeedsTheGraphs() {
    assertThatThrownBy(() -> FlowNullnessCheck.of(DEREF, "m").matches(null))
        .isInstanceOf(AnalysisFailedException.class);
  }

  @Test
  void aNullAssignedInBothBranchesIsNullAfterwards() {
    assertThat(
            run(
                DEREF,
                "boolean b",
                "String s = \"a\";\nif (b) { s = null; } else { s = null; }\ns.length();"))
        .containsExactly("5:1");
  }

  @Test
  void aNullInOneBranchIsNullOnSomePath() {
    assertThat(run(SOME, "boolean b", "String s = \"a\";\nif (b) { s = null; }\ns.length();"))
        .containsExactly("5:1");
    assertThat(run(DEREF, "boolean b", "String s = \"a\";\nif (b) { s = null; }\ns.length();"))
        .isEmpty();
  }

  @Test
  void aNullTestNarrowsBothEdgesAndANegatedTestFlipsThem() {
    assertThat(run(DEREF, "", "String s = null;\nif (s != null) { s.length(); }")).isEmpty();
    assertThat(run(DEREF, "", "String s = null;\nif (!(s == null)) { s.length(); }")).isEmpty();
    assertThat(run(DEREF, "", "String s = null;\nif (s == null) { s.length(); }"))
        .containsExactly("4:18");
    assertThat(run(DEREF, "", "String s = null;\nif (null == s) { s.length(); }"))
        .containsExactly("4:18");
  }

  @Test
  void anInstanceofTestMakesTheOperandNonNull() {
    assertThat(
            run(
                SOME,
                "boolean b",
                "Object o = null;\nif (b) { o = \"x\"; }\nif (o instanceof String) { o.hashCode(); }"))
        .isEmpty();
  }
}
