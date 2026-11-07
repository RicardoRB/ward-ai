package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleOptionsTest {

  private static final long FOUR = 4L;

  private static final String ID = "ward:java/method-length";
  private static final String TOML_BODY = "check = \"lines\"\nmax = 60\nmessage = \"m\"\n";

  @Test
  void replacesTheLineOfATomlBody() {
    final RuleDefinition tuned = RuleOptions.apply(rule(TOML_BODY), Map.of("max", 80L));

    assertThat(tuned.body()).isEqualTo("check = \"lines\"\nmax = 80\nmessage = \"m\"\n");
  }

  @Test
  void addsAKeyTheTomlBodyDoesNotSetAndQuotesText() {
    final RuleDefinition tuned = RuleOptions.apply(rule(TOML_BODY), Map.of("pattern", "a\"b\\"));

    assertThat(tuned.body()).endsWith("\npattern = \"a\\\"b\\\\\"\n");
  }

  @Test
  void replacesAPairOfAOneLineBody() {
    final RuleDefinition tuned =
        RuleOptions.apply(rule("measure=lines; max=60; message=m"), Map.of("max", 7L));

    assertThat(tuned.body()).isEqualTo("measure=lines; max=7; message=m");
  }

  @Test
  void anEmptyBodyGainsThePair() {
    assertThat(RuleOptions.apply(rule(""), Map.of("max", FOUR)).body()).isEqualTo("max=4");
  }

  @Test
  void aListIsWrittenInTheLayoutOfTheBody() {
    final RuleDefinition toml =
        RuleOptions.apply(rule(TOML_BODY), Map.of("names", List.of("a", "b")));
    final RuleDefinition pairs = RuleOptions.apply(rule("x=1"), Map.of("names", List.of("a", "b")));

    assertThat(toml.body()).contains("names = [\"a\", \"b\"]");
    assertThat(pairs.body()).isEqualTo("x=1; names=a,b");
  }

  @Test
  void anUndeclaredParameterIsRejected() {
    assertThatThrownBy(() -> RuleOptions.apply(rule(TOML_BODY), Map.of("node", "x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown option 'node'");
  }

  @Test
  void aSemicolonCannotBeWrittenInAOneLineBody() {
    assertThatThrownBy(() -> RuleOptions.apply(rule("x=1"), Map.of("names", "a;b")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RuleDefinition rule(final String body) {
    return new RuleDefinition(
        ID,
        Set.of(AnalysisLanguage.JAVA),
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        body,
        "sizes",
        Set.of(RulePreset.STANDARD),
        List.of(
            RuleOption.integer("max", 1),
            new RuleOption("pattern", RuleOptionType.STRING),
            new RuleOption("names", RuleOptionType.STRING_LIST)),
        0);
  }
}
