package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.config.WardException;
import java.util.List;

/** Builds the rule setup of the {@code openapi-lint} gate. */
public final class OpenApiRuleSetups {

  private OpenApiRuleSetups() {}

  /**
   * Returns the setup that runs the OpenAPI rules chosen by the rule configuration file, {@code
   * ward-rules.toml} unless {@code rulesFile} names another, or all of them without one.
   *
   * @param checkName the gate's name, for messages
   * @param rulesFile the {@code rules-file} path, or {@code null}
   * @param analysis the adapters that load the rules and read the configuration
   * @throws WardException if the rule files are unusable
   */
  public static RuleSetup create(
      final String checkName, final String rulesFile, final AnalysisPorts analysis) {
    final AnalysisRuleLoad loaded = analysis.rules().load();
    if (!loaded.configurationErrors().isEmpty()) {
      throw new WardException(String.join(" ", loaded.configurationErrors()));
    }
    final List<RuleDefinition> available = loaded.rules();
    final RuleSetup configured =
        new ConfiguredRuleSetup(
            checkName,
            rulesFile,
            new ConfiguredRuleSetup.Choices(available, false, available, List.of()),
            analysis.rules());
    return LanguageFilteredSetup.onlyOpenApi(configured);
  }
}
