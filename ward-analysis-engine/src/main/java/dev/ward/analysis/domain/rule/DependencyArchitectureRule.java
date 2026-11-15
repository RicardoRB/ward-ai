package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@code ward:architecture/dependencies} rule: for each {@code must-not-depend-on}, {@code
 * may-only-depend-on} and {@code only-accessed-by} rule of {@code ward-architecture.toml}, reports
 * every dependency edge that breaks it, at the import that creates the edge.
 */
public final class DependencyArchitectureRule implements GraphRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/dependencies";

  private final RuleDefinition definition;
  private final List<ArchitectureRule> rules;

  /**
   * Creates the rule that enforces the rules in the body of {@code definition}.
   *
   * @throws NullPointerException if the definition is missing
   * @throws IllegalArgumentException if the body is not a list of rules
   */
  public DependencyArchitectureRule(final RuleDefinition definition) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.rules =
        ArchitectureRuleBody.decode(definition.body()).stream()
            .filter(rule -> rule.constraint().kind().readsEdges())
            .toList();
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    final List<GraphMatch> matches = new ArrayList<>();
    for (final ArchitectureRule rule : rules) {
      final Predicate<String> selected = rule.select().matcher();
      final Predicate<String> target = rule.constraint().target().matcher();
      for (final DependencyEdge edge : graph.edges()) {
        violation(rule, edge, selected, target)
            .ifPresent(
                problem ->
                    matches.add(
                        new GraphMatch(
                            edge.from(),
                            new RuleMatch(edge.reference().span(), rule.message(problem)))));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<String> violation(
      final ArchitectureRule rule,
      final DependencyEdge edge,
      final Predicate<String> selected,
      final Predicate<String> target) {
    return switch (rule.constraint().kind()) {
      case MUST_NOT_DEPEND_ON -> forbidden(edge, selected, target);
      case MAY_ONLY_DEPEND_ON -> outside(edge, selected, target);
      case ONLY_ACCESSED_BY -> accessed(edge, selected, target);
      default -> Optional.empty();
    };
  }

  private static Optional<String> forbidden(
      final DependencyEdge edge, final Predicate<String> selected, final Predicate<String> target) {
    return selected.test(edge.from()) && target.test(edge.to())
        ? Optional.of(edge.from() + " must not depend on " + edge.to())
        : Optional.empty();
  }

  private static Optional<String> outside(
      final DependencyEdge edge, final Predicate<String> selected, final Predicate<String> target) {
    return selected.test(edge.from()) && !selected.test(edge.to()) && !target.test(edge.to())
        ? Optional.of(edge.from() + " may not depend on " + edge.to())
        : Optional.empty();
  }

  private static Optional<String> accessed(
      final DependencyEdge edge, final Predicate<String> selected, final Predicate<String> target) {
    return selected.test(edge.to()) && !selected.test(edge.from()) && !target.test(edge.from())
        ? Optional.of(edge.to() + " may not be accessed by " + edge.from())
        : Optional.empty();
  }
}
