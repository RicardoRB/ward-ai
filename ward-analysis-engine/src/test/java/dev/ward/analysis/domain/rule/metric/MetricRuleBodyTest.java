package dev.ward.analysis.domain.rule.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reads metric rule bodies and rejects the ones that are not valid metric rules. */
final class MetricRuleBodyTest {

  private static final int PARAMETER_LIMIT = 5;

  private static final String VALID =
      "measure=lines; node=funcdef; max=60; message=too long {value}";

  @Test
  void compileKeepsTheDefinitionOfTheRule() {
    final RuleDefinition definition = definition(VALID);

    final CompiledRule rule = new MetricMatcher().compile(definition);

    assertThat(rule.definition()).isEqualTo(definition);
  }

  @Test
  void parsesTargetsWithStylesAndTheBodyKeys() {
    final MetricRule rule =
        MetricRuleBody.parse(
            "measure=parameters; node=funcdef:snake,classdef:cap; max=5; name=name; path=parameters/typedargslist/tfpdef; receivers=self,cls; nested=funcdef; decisions=if_stmt; message=m {value}");

    assertThat(rule.measurement()).isEqualTo(Measurement.PARAMETERS);
    assertThat(rule.targets())
        .containsExactly(
            new MetricRule.Target("funcdef", NameStyle.SNAKE),
            new MetricRule.Target("classdef", NameStyle.CAP));
    assertThat(rule.max()).isEqualTo(PARAMETER_LIMIT);
    assertThat(rule.keys().parameterPaths())
        .containsExactly(List.of("parameters", "typedargslist", "tfpdef"));
    assertThat(rule.keys().receivers()).containsExactlyInAnyOrder("self", "cls");
    assertThat(rule.keys().nested()).containsExactly("funcdef");
    assertThat(rule.keys().decisions()).containsExactly("if_stmt");
  }

  @Test
  void aBodyWithoutAMaximumUsesZero() {
    assertThat(MetricRuleBody.parse("measure=identifier-case; node=funcdef:snake; message=m").max())
        .isZero();
  }

  @Test
  void rejectsAPartWithoutAnEquals() {
    assertRejected("measure=lines; node=funcdef; max=1; message=m; stray");
  }

  @Test
  void rejectsAnUnknownMeasurement() {
    assertRejected("measure=bogus; node=funcdef; max=1; message=m");
  }

  @Test
  void rejectsAnUnknownStyle() {
    assertRejected("measure=identifier-case; node=funcdef:bogus; message=m");
  }

  @Test
  void rejectsAMissingMessage() {
    assertRejected("measure=lines; node=funcdef; max=1");
  }

  @Test
  void rejectsAMissingNode() {
    assertRejected("measure=lines; max=1; message=m");
  }

  private static void assertRejected(final String body) {
    assertThatThrownBy(() -> MetricRuleBody.parse(body))
        .isInstanceOf(AnalysisFailedException.class);
  }

  private static RuleDefinition definition(final String body) {
    return new RuleDefinition(
        "ward:python/test",
        AnalysisLanguage.PYTHON,
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        body);
  }
}
