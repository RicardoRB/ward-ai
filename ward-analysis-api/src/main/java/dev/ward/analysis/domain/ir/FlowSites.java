package dev.ward.analysis.domain.ir;

import java.util.List;

/**
 * Finds the flow sites of the nodes a language lowering adds to a control-flow graph. The builder
 * asks for the sites of each node when the node is added, in evaluation order.
 */
@FunctionalInterface
public interface FlowSites {

  /** Finds no sites. */
  FlowSites NONE = node -> List.of();

  /**
   * Returns the flow sites of a node, inner sites before the sites that contain them.
   *
   * @param node a statement or condition node
   * @return the sites of the node, in evaluation order
   */
  List<FlowSite> of(SyntaxNode node);
}
