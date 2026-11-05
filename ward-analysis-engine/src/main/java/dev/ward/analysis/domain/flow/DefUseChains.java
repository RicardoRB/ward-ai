package dev.ward.analysis.domain.flow;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Def-use chains of one graph: for each write of a variable, the reads and captures that its value
 * can reach. A write that reaches nothing has an empty list.
 */
public final class DefUseChains {

  private final Map<AccessSite, List<AccessSite>> chains;

  private DefUseChains(final Map<AccessSite, List<AccessSite>> chains) {
    this.chains = chains;
  }

  /**
   * Builds the chains of a graph from its reaching definitions.
   *
   * @param graph the graph to analyse
   * @return the chains, one per write, in block order
   */
  public static DefUseChains of(final ControlFlowGraph graph) {
    return of(ReachingDefinitions.of(graph));
  }

  /**
   * Builds the chains from reaching definitions that were already computed.
   *
   * @param reaching the reaching definitions of a graph
   * @return the chains, one per write, in block order
   */
  public static DefUseChains of(final ReachingDefinitions reaching) {
    final Map<AccessSite, List<AccessSite>> chains = new LinkedHashMap<>();
    reaching.definitions().forEach(definition -> chains.put(definition, new ArrayList<>()));
    for (final AccessSite use : reaching.uses()) {
      for (final AccessSite definition : reaching.reaching(use)) {
        chains.get(definition).add(use);
      }
    }
    chains.replaceAll((definition, uses) -> List.copyOf(uses));
    return new DefUseChains(chains);
  }

  /** Returns the writes of the graph, in block order. */
  public List<AccessSite> definitions() {
    return List.copyOf(chains.keySet());
  }

  /**
   * Returns the reads and captures that a write reaches.
   *
   * @param definition a write of the graph
   * @return the reads, in block order; empty when nothing reads the value
   */
  public List<AccessSite> usesOf(final AccessSite definition) {
    return chains.getOrDefault(definition, List.of());
  }
}
