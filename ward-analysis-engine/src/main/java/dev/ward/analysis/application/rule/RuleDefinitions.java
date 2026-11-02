package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The built-in analysis rules, one row per rule. Language rules arrive in steps 1.4 to 1.8, so the
 * table has no rows yet.
 */
public final class RuleDefinitions {
  private static final List<RuleDefinition> ALL = List.of();

  private RuleDefinitions() {}

  /** Returns every built-in rule in table order. */
  public static List<RuleDefinition> all() {
    return ALL;
  }

  /**
   * Returns a copy of {@code rules} after checking that no two share an id.
   *
   * @throws RuleConfigurationException if two rules share an id
   */
  public static List<RuleDefinition> requireUnique(final List<RuleDefinition> rules) {
    final Set<String> ids = new HashSet<>();
    for (final RuleDefinition rule : rules) {
      if (!ids.add(rule.id())) {
        throw new RuleConfigurationException(
            "Analysis rule id '"
                + rule.id()
                + "' is defined more than once. Each rule needs its own id.");
      }
    }
    return List.copyOf(rules);
  }
}
