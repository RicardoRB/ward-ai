package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The {@code ward:architecture/location} rule: for each {@code must-be-in} rule of {@code
 * ward-architecture.toml}, reports every selected source file that the constraint's selector does
 * not select, at the first line of the file. It reads the files of the run, not the edges, so it
 * applies to every language.
 */
public final class LocationArchitectureRule implements GraphRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/location";

  private static final Span FIRST_LINE = new Span(1, 1, 1, 1, 0, 0);

  private final RuleDefinition definition;
  private final List<ArchitectureRule> rules;

  /**
   * Creates the rule that enforces the rules in the body of {@code definition}.
   *
   * @throws NullPointerException if the definition is missing
   * @throws IllegalArgumentException if the body is not a list of rules
   */
  public LocationArchitectureRule(final RuleDefinition definition) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.rules =
        ArchitectureRuleBody.decode(definition.body()).stream()
            .filter(rule -> rule.constraint().kind() == ArchitectureConstraint.Kind.MUST_BE_IN)
            .toList();
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  /** Reads no edges: without the files of the run the rule reports nothing. */
  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    return List.of();
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph, final ProjectFiles project) {
    final List<GraphMatch> matches = new ArrayList<>();
    for (final ArchitectureRule rule : rules) {
      final Predicate<String> selected = rule.select().matcher();
      final Predicate<String> expected = rule.constraint().target().matcher();
      project.sources().keySet().stream()
          .sorted()
          .filter(selected)
          .filter(expected.negate())
          .forEach(
              path ->
                  matches.add(
                      new GraphMatch(
                          path,
                          new RuleMatch(
                              FIRST_LINE,
                              rule.message(path + " is not in the expected location")))));
    }
    return List.copyOf(matches);
  }
}
