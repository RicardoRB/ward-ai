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
    return new SecretGate(check, setup(check), ports, history, focus);
  }

  /**
   * Builds the scan of agent prompts for {@code check}.
   *
   * @throws WardException if a rule file is unusable
   */
  public PromptScan promptScan(final GateConfiguration check) {
    return new PromptScan(check, promptSetup(check), ports);
  }

  /**
   * Loads only the secret rules, unless the project has a rule configuration file: that file may
   * name rules of any language, so it is resolved against the whole catalog.
   */
  private RuleSetup promptSetup(final GateConfiguration check) {
    return (root, release) ->
        (ports.rules().configuration(root, check.rulesFile()).isPresent()
            ? setup(check)
            : setup(check, ports.rules().loadSecrets()))
            .resolve(root, release);
  }

  private RuleSetup setup(final GateConfiguration check) {
    return setup(check, ports.rules().load());
  }

  private RuleSetup setup(final GateConfiguration check, final AnalysisRuleLoad loaded) {
    if (!loaded.configurationErrors().isEmpty()) {
      throw new WardException(String.join(" ", loaded.configurationErrors()));
    }
    final List<RuleDefinition> text =
        loaded.rules().stream().filter(rule -> rule.capability() == RuleCapability.TEXT).toList();
    return new ConfiguredRuleSetup(
        check.name(),
        check.rulesFile(),
        new ConfiguredRuleSetup.Choices(text, false, loaded.rules(), List.of()),
        ports.rules()
    );
  }
}
