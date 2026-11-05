package dev.ward.analysis.domain.rule.graph;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;

/** Builds the graph rule that a definition names, for the rule ids the graph rules own. */
public final class GraphRules {

  /** The ids of the graph rules, which the per-unit engine never runs. */
  public static final List<String> IDS = List.of(SliceCycleRule.ID, IndependenceRule.ID);

  private GraphRules() {}

  /** Whether {@code definition} is a graph rule. */
  public static boolean isGraphRule(final RuleDefinition definition) {
    return IDS.contains(definition.id());
  }

  /**
   * Returns the graph rule of {@code definition}.
   *
   * @throws IllegalArgumentException if the id is not a graph rule or the body is malformed
   */
  public static GraphRule compile(final RuleDefinition definition) {
    return switch (definition.id()) {
      case SliceCycleRule.ID ->
          new SliceCycleRule(definition, GraphRuleBody.decodeRoots(definition.body()));
      case IndependenceRule.ID ->
          new IndependenceRule(definition, GraphRuleBody.decodeTables(definition.body()));
      default -> throw new IllegalArgumentException("not a graph rule: " + definition.id());
    };
  }
}
