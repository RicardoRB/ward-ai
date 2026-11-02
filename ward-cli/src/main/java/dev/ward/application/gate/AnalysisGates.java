package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;
import java.util.ArrayList;
import java.util.List;

/** Builds the {@code ward} gate and selects the rules its check names. */
final class AnalysisGates {

  private AnalysisGates() {}

  /**
   * Builds the gate for {@code check}, failing the configuration when a rule file is unusable or a
   * named rule id is unknown.
   */
  static QualityGate create(final GateConfiguration check, final AnalysisPorts analysis) {
    if (analysis == null) {
      throw new WardException("Analysis gate dependencies are unavailable.");
    }
    final AnalysisRuleLoad loaded = analysis.rules().load();
    if (!loaded.configurationErrors().isEmpty()) {
      throw new WardException(String.join(" ", loaded.configurationErrors()));
    }
    return new AnalysisGate(check.name(), selectRules(check, loaded.rules()), analysis);
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
