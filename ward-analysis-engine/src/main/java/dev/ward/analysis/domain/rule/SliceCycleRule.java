package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FlowStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code ward:architecture/cycles} rule: collapses the file graph into slices and reports each
 * group of two or more slices that depend on each other in a cycle, once. The finding sits at the
 * edge that leaves the group's alphabetically first slice, and its path lists one step per slice
 * edge of the shortest cycle through that slice.
 */
public final class SliceCycleRule implements GraphRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/cycles";

  private final RuleDefinition definition;
  private final SliceMembership membership;

  /**
   * Creates the rule that slices the files below {@code roots}, described by {@code definition}.
   *
   * @throws NullPointerException if a field is missing
   */
  public SliceCycleRule(final RuleDefinition definition, final List<String> roots) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.membership = new SliceMembership(List.copyOf(roots));
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    final List<GraphMatch> matches = new ArrayList<>();
    for (final CycleSearch.Cycle cycle : CycleSearch.find(graph.edges(), membership::sliceOf)) {
      matches.add(cycleMatch(cycle));
    }
    return List.copyOf(matches);
  }

  private static GraphMatch cycleMatch(final CycleSearch.Cycle cycle) {
    final List<String> ring = cycle.slices();
    final List<FlowStep> steps = new ArrayList<>();
    for (int index = 0; index < cycle.edges().size(); index++) {
      final DependencyEdge edge = cycle.edges().get(index);
      steps.add(
          new FlowStep(
              edge.reference().span(),
              ring.get(index) + " depends on " + ring.get(index + 1),
              edge.from()));
    }
    final DependencyEdge first = cycle.edges().getFirst();
    return new GraphMatch(
        first.from(),
        new RuleMatch(
            first.reference().span(), "slices form a cycle: " + String.join(" -> ", ring), steps));
  }
}
