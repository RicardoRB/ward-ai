package dev.ward.analysis.domain.rule.graph;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code ward:architecture/independence} rule: in each table of modules, reports every
 * dependency edge from a file of one module to a file of another listed module, at its import. A
 * file is in the longest listed module that is a directory above it.
 */
public final class IndependenceRule implements GraphRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/independence";

  private final RuleDefinition definition;
  private final List<List<String>> tables;

  /**
   * Creates the rule that enforces {@code tables}, described by {@code definition}.
   *
   * @throws NullPointerException if a field is missing
   */
  public IndependenceRule(final RuleDefinition definition, final List<List<String>> tables) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.tables = tables.stream().map(List::copyOf).toList();
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    final List<GraphMatch> matches = new ArrayList<>();
    for (final DependencyEdge edge : graph.edges()) {
      for (final List<String> table : tables) {
        violation(edge, table).ifPresent(matches::add);
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<GraphMatch> violation(
      final DependencyEdge edge, final List<String> modules) {
    final Optional<String> from = moduleOf(edge.from(), modules);
    final Optional<String> to = moduleOf(edge.to(), modules);
    if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
      return Optional.empty();
    }
    return Optional.of(
        new GraphMatch(
            edge.from(),
            new RuleMatch(
                edge.reference().span(), from.get() + " must not depend on " + to.get())));
  }

  /** The longest module that is a directory above {@code path}, or empty when none is. */
  private static Optional<String> moduleOf(final String path, final List<String> modules) {
    return modules.stream()
        .filter(module -> path.startsWith(module + "/"))
        .max(Comparator.comparingInt(String::length));
  }
}
