package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.application.gate.AnalysisRuleLoad;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ClasspathRuleSourceTest {

  private static final List<String> SCOPE_CHECKS =
      List.of(
          "node-call-without-follow-up",
          "node-hidden-field",
          "node-prefer-const",
          "node-type-only-import",
          "node-variable-distance",
          "reassigned-binding",
          "undefined-name",
          "unused-binding");

  @Test
  void malformedFileIsReportedAndOtherFilesStillLoad() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(
                List.of("fixtures/rules/malformed.toml", "fixtures/rules/valid.toml"))
            .load();

    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/malformed.toml")
        .contains("not valid TOML");
    assertThat(load.rules())
        .extracting(RuleDefinition::id)
        .containsExactly("ward:java/method-length");
  }

  @Test
  void ruleWithUnknownCheckIsReportedAndSkipped() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/bad-body.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/bad-body.toml")
        .contains("unknown check 'no-such-check'");
  }

  @Test
  void packagedJavaRuleFilesLoadWithoutErrorsAndDistinctIds() {
    final AnalysisRuleLoad load = new ClasspathRuleSource().load();

    assertThat(load.configurationErrors()).isEmpty();
    final List<RuleDefinition> java =
        load.rules().stream().filter(rule -> rule.covers(AnalysisLanguage.JAVA)).toList();
    assertThat(java)
        .extracting(RuleDefinition::id)
        .isNotEmpty()
        .doesNotHaveDuplicates()
        .allMatch(id -> id.startsWith("ward:java/"));
    assertThat(java)
        .allSatisfy(
            rule -> {
              assertThat(rule.languages()).containsExactly(AnalysisLanguage.JAVA);
              assertThat(rule.capability()).isEqualTo(capabilityOf(rule));
              assertThat(rule.severity()).isEqualTo(AnalysisSeverity.ERROR);
            });
  }

  @Test
  void bundledNodeRulesLoadWithOneRowPerLanguageAndMetric() {
    final AnalysisRuleLoad load = new ClasspathRuleSource().load();

    assertThat(load.configurationErrors()).isEmpty();
    final List<RuleDefinition> node =
        load.rules().stream()
            .filter(
                rule ->
                    rule.covers(AnalysisLanguage.TYPESCRIPT)
                        || rule.covers(AnalysisLanguage.JAVASCRIPT))
            .toList();
    assertThat(node).isNotEmpty();
    assertThat(node)
        .allSatisfy(
            rule -> {
              assertThat(rule.capability()).isEqualTo(capabilityOf(rule));
              assertThat(rule.body().isEmpty() || rule.body().contains("check = ")).isTrue();
            });
    assertThat(node).extracting(RuleDefinition::id).doesNotHaveDuplicates();
    assertThat(node).anyMatch(rule -> rule.covers(AnalysisLanguage.TYPESCRIPT));
    assertThat(node).anyMatch(rule -> rule.covers(AnalysisLanguage.JAVASCRIPT));
  }

  @Test
  void rowWhoseIdNamesAnotherLanguageIsRejectedAndTheErrorNamesTheId() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/wrong-language.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/wrong-language.toml")
        .contains("ward:javascript/naming");
  }

  @Test
  void missingFileIsReportedAsConfigurationError() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/absent.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/absent.toml")
        .contains("missing");
  }

  @Test
  void duplicateIdAcrossFilesSkipsTheLaterFile() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/valid.toml", "fixtures/rules/valid.toml"))
            .load();

    assertThat(load.rules()).hasSize(1);
    assertThat(load.configurationErrors()).singleElement().asString().contains("more than once");
  }

  /** Every rule reads syntax except the data-flow rule, which reads the control-flow graphs. */
  private static boolean isJavaInjection(final String id) {
    return id.startsWith("ward:java/") && id.endsWith("-injection");
  }

  private static RuleCapability capabilityOf(final RuleDefinition rule) {
    if (rule.capability() == RuleCapability.CFG
        || rule.body().contains("check = \"unreachable-code\"")) {
      return RuleCapability.CFG;
    }
    if (SCOPE_CHECKS.stream()
        .anyMatch(check -> rule.body().contains("check = \"" + check + "\""))) {
      return RuleCapability.SCOPES;
    }
    if (rule.capability() == RuleCapability.TYPES) {
      return RuleCapability.TYPES;
    }
    return readsDataFlow(rule) ? RuleCapability.DATAFLOW : RuleCapability.SYNTAX;
  }

  private static boolean readsDataFlow(final RuleDefinition rule) {
    return rule.id().endsWith("/unused-assignment")
        || isJavaInjection(rule.id())
        || Stream.of("unused-assignment", "dead-store", "taint")
            .anyMatch(check -> rule.body().contains("check = \"" + check + "\""));
  }
}
