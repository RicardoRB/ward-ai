package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The rules one {@code ward} run executes after configuration.
 *
 * @param rules the rules, with configured options and severity applied
 * @param excludePaths by rule id, the patterns of the paths that rule leaves out
 * @param problems the configuration problems; when it is not empty no rule runs
 */
public record RuleSet(
    List<RuleDefinition> rules, Map<String, List<Pattern>> excludePaths, List<String> problems) {

  /** Copies the parts. */
  public RuleSet {
    rules = List.copyOf(rules);
    excludePaths = Map.copyOf(excludePaths);
    problems = List.copyOf(problems);
  }

  /** Returns a set that runs {@code rules} unchanged. */
  public static RuleSet of(final List<RuleDefinition> rules) {
    return new RuleSet(rules, Map.of(), List.of());
  }
}
