package dev.ward.analysis.domain.rule.metric;

import java.util.List;
import java.util.Set;

/**
 * A parsed metric rule: what to measure, on which node kinds, and the limit that a violation
 * exceeds. Built from the body of a {@code RuleDefinition}.
 *
 * @param measurement the measurement to take
 * @param targets the node kinds the measurement applies to, each with the style it expects
 * @param max the largest value that is not a violation
 * @param message the message template, with {@code {name}}, {@code {value}}, {@code {max}} and
 *     {@code {expected}} placeholders
 * @param keys the body keys that the measurement reads, such as the path to parameters
 */
record MetricRule(
    Measurement measurement, List<Target> targets, int max, String message, MetricRuleKeys keys) {

  /** A node kind to measure, with the style its declared names must follow, if any. */
  record Target(String kind, NameStyle style) {}

  /**
   * Grammar-specific names that the body supplies, so the matcher does not depend on a language.
   *
   * @param name the child kind that holds the declared identifier
   * @param parameterPaths the alternative paths from the function node to its parameter items, each
   *     one's last kind is the item
   * @param receivers the first-parameter names that a method does not count
   * @param nested the node kinds whose decision points belong to their own scope
   * @param decisions the decision node kinds, or {@code parent>token} pairs for tokens
   */
  record MetricRuleKeys(
      String name,
      List<List<String>> parameterPaths,
      Set<String> receivers,
      Set<String> nested,
      Set<String> decisions) {}
}
