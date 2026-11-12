package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.secrets.GitHistory;
import java.util.List;
import javax.inject.Inject;

/** Builds the {@code secrets} gate over the analysis ports and the Git history port. */
public final class SecretGates {
  private final AnalysisPorts ports;
  private final GitHistory history;

  /** Creates the builder. */
  @Inject
  public SecretGates(final AnalysisPorts ports, final GitHistory history) {
    this.ports = ports;
    this.history = history;
  }

  /**
   * Builds the gate for {@code check}, which scans only the root-relative paths in {@code focus},
   * or every text file when it is empty.
   *
   * @throws WardException if a rule file is unusable
   */
  public QualityGate create(final GateConfiguration check, final List<String> focus) {
    final AnalysisRuleLoad loaded = ports.rules().load();
    if (!loaded.configurationErrors().isEmpty()) {
      throw new WardException(String.join(" ", loaded.configurationErrors()));
    }
    final List<RuleDefinition> text =
        loaded.rules().stream().filter(rule -> rule.capability() == RuleCapability.TEXT).toList();
    final RuleSetup setup =
        new ConfiguredRuleSetup(
            check.name(),
            check.rulesFile(),
            new ConfiguredRuleSetup.Choices(text, false, loaded.rules(), List.of()),
            ports.rules());
    return new SecretGate(check, setup, ports, history, focus);
  }
}
