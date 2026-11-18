package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.secrets.GitHistory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
   * Resolves only the secret rules: the catalog is the secret rules, and a {@code ward-rules.toml}
   * is cut to what it says about them (their {@code [rules.secrets.*]} tables and the {@code
   * secrets.*} entries of {@code [select]}), because a secret rule is always selected and nothing
   * else runs on a prompt.
   */
  private RuleSetup promptSetup(final GateConfiguration check) {
    return setup(check, ports.rules().loadSecrets(), new SecretsOnly(ports.rules()));
  }

  private RuleSetup setup(final GateConfiguration check) {
    return setup(check, ports.rules().load(), ports.rules());
  }

  private RuleSetup setup(
      final GateConfiguration check,
      final AnalysisRuleLoad loaded,
      final AnalysisRuleSource source
  ) {
    if (!loaded.configurationErrors().isEmpty()) {
      throw new WardException(String.join(" ", loaded.configurationErrors()));
    }
    final List<RuleDefinition> text =
        loaded.rules().stream().filter(rule -> rule.capability() == RuleCapability.TEXT).toList();
    return new ConfiguredRuleSetup(
        check.name(),
        check.rulesFile(),
        new ConfiguredRuleSetup.Choices(text, false, loaded.rules(), List.of()),
        source
    );
  }

  /** The rule source of a prompt scan: the secret rules and the secret part of the configuration. */
  private static final class SecretsOnly implements AnalysisRuleSource {
    private static final String PREFIX = "secrets.";
    private static final String ID_PREFIX = "ward:secrets/";

    private final AnalysisRuleSource source;

    SecretsOnly(final AnalysisRuleSource source) {
      this.source = source;
    }

    @Override
    public AnalysisRuleLoad load() {
      return source.loadSecrets();
    }

    @Override
    public Optional<RuleConfigFile> configuration(final Path root, final String file) {
      return source.configuration(root, file).map(SecretsOnly::cut);
    }

    private static RuleConfigFile cut(final RuleConfigFile file) {
      final RuleConfigFile.Select select = file.select();
      final Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
      file.rules().forEach(
          (id, table) -> {
            if (id.startsWith(ID_PREFIX)) {
              rules.put(id, table);
            }
          }
      );
      return new RuleConfigFile(
          file.name(),
          new RuleConfigFile.Select(
              select.preset(),
              select.languages(),
              select.categories(),
              secrets(select.include()),
              secrets(select.exclude())
          ),
          rules,
          file.problems()
      );
    }

    private static List<String> secrets(final List<String> names) {
      return names.stream().filter(name -> name.startsWith(PREFIX)).toList();
    }
  }
}
