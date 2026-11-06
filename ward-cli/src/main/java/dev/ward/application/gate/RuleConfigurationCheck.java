package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Checks the rule configuration file of a {@code ward} check without running the gate. */
public final class RuleConfigurationCheck {

  private RuleConfigurationCheck() {}

  /**
   * Resolves the rule configuration of {@code check} in the project at {@code root}. Returns empty
   * when the check has no configuration file; otherwise the rules it selects, or the problems that
   * stop the gate.
   */
  public static Optional<RuleSet> resolve(
      final GateConfiguration check, final AnalysisRuleSource source, final Path root) {
    if (source.configuration(root, check.rulesFile()).isEmpty()) {
      return Optional.empty();
    }
    final List<dev.ward.analysis.domain.rule.RuleDefinition> available = source.load().rules();
    return Optional.of(
        new ConfiguredRuleSetup(
                check.name(),
                check.rulesFile(),
                new ConfiguredRuleSetup.Choices(
                    List.of(), !check.rules().isEmpty(), available, List.of()),
                source)
            .resolve(root));
  }
}
