package dev.ward.analysis.domain.rule.graph;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;

/**
 * A rule over the whole dependency graph of a run rather than over one file. A cycle or an
 * independence rule needs every edge at once, so it runs once per run and is never cached per unit.
 */
public interface GraphRule {

  /** Returns the definition this rule was built from. */
  RuleDefinition definition();

  /** Returns the matches of this rule in {@code graph}, each at the file that creates it. */
  List<GraphMatch> matches(DependencyGraph graph);
}
