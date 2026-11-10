package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds the {@code ward} gate and selects the rules its check names. */
final class AnalysisGates {
  private static final Logger LOGGER = Logger.getLogger(AnalysisGates.class.getName());

  private AnalysisGates() {}

  /**
   * Builds the gate for {@code check}, failing the configuration when a rule file is unusable or a
   * named rule id is unknown.
   */
  static QualityGate create(final GateConfiguration check, final AnalysisPorts analysis) {
    return create(check, analysis, List.of());
  }

  /**
   * Builds the gate for {@code check} that reports only the findings touching {@code focus}, the
   * root-relative changed paths, or every finding when {@code focus} is empty.
   */
  static QualityGate create(
      final GateConfiguration check, final AnalysisPorts analysis, final List<String> focus) {
    if (analysis == null) {
      throw new WardException("Analysis gate dependencies are unavailable.");
    }
    final AnalysisRuleLoad loaded = analysis.rules().load();
    if (!loaded.configurationErrors().isEmpty()) {
      throw new WardException(String.join(" ", loaded.configurationErrors()));
    }
    final RuleSetup setup =
        new ConfiguredRuleSetup(
            check.name(),
            check.rulesFile(),
            new ConfiguredRuleSetup.Choices(
                selectRules(check, loaded.rules()),
                !check.rules().isEmpty(),
                loaded.rules(),
                architectureRules(check)),
            analysis.rules());
    return new AnalysisGate(check.name(), setup, analysis, check.javaClasspath(), focus);
  }

  /**
   * The layer rule when the check configures architecture boundaries, and the cycle and
   * independence rules when it configures their tables.
   */
  private static List<RuleDefinition> architectureRules(final GateConfiguration check) {
    final List<RuleDefinition> rules = new ArrayList<>();
    if (!check.layers().isEmpty()) {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("Architecture boundaries configured: " + check.layers().size());
      }
      rules.add(LayerRules.definition(check.layers()));
    }
    if (!check.cycleRoots().isEmpty()) {
      rules.add(ArchitectureGraphRules.cycles(check.cycleRoots()));
    }
    if (!check.independence().isEmpty()) {
      rules.add(ArchitectureGraphRules.independence(check.independence()));
    }
    return rules;
  }

  /** Returns the rules the check names, in its order, or every loaded rule when it names none. */
  private static List<RuleDefinition> selectRules(
      final GateConfiguration check, final List<RuleDefinition> available) {
    if (check.rules().isEmpty()) {
      return available;
    }
    final List<RuleDefinition> selected = new ArrayList<>();
    for (final String id : check.rules()) {
      selected.add(
          available.stream()
              .filter(rule -> rule.id().equals(id))
              .findFirst()
              .orElseThrow(() -> unknownRule(check, id, available)));
    }
    return selected;
  }

  private static WardException unknownRule(
      final GateConfiguration check, final String id, final List<RuleDefinition> available) {
    final String known =
        available.isEmpty()
            ? "No analysis rules are installed."
            : "Known rules: " + available.stream().map(RuleDefinition::id).toList() + ".";
    return new WardException(
        "Rule id '"
            + id
            + "' of check '"
            + check.name()
            + "' is not a known analysis rule. "
            + known);
  }
}
