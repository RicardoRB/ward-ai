package dev.ward.analysis.domain.rule;

import java.util.List;

/** Builds the graph rule that a definition names, for the rule ids the graph rules own. */
public final class GraphRules {

  /** The ids of the graph rules, which the per-unit engine never runs. */
  public static final List<String> IDS =
      List.of(
          SliceCycleRule.ID,
          IndependenceRule.ID,
          UnusedFileRule.ID,
          CyclicImportRule.ID,
          DependencyArchitectureRule.ID,
          NamingArchitectureRule.ID,
          LocationArchitectureRule.ID);

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
      case UnusedFileRule.ID ->
          new UnusedFileRule(definition, GraphRuleBody.decodeNames(definition.body()));
      case CyclicImportRule.ID -> new CyclicImportRule(definition);
      case DependencyArchitectureRule.ID -> new DependencyArchitectureRule(definition);
      case NamingArchitectureRule.ID -> new NamingArchitectureRule(definition);
      case LocationArchitectureRule.ID -> new LocationArchitectureRule(definition);
      default -> throw new IllegalArgumentException("not a graph rule: " + definition.id());
    };
  }
}
