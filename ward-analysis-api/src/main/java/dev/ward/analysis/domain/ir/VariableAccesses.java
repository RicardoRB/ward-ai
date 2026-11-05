package dev.ward.analysis.domain.ir;

import java.util.List;

/**
 * Finds the variable accesses of the nodes a language lowering adds to a control-flow graph. The
 * builder asks for the accesses of each node when the node is added, so the calls come in
 * evaluation order.
 */
@FunctionalInterface
public interface VariableAccesses {

  /** Records nothing; a graph built with it has no accesses. */
  VariableAccesses NONE = node -> List.of();

  /**
   * Returns the accesses of a node added to the current block, in evaluation order.
   *
   * @param node the statement or condition node
   * @return the accesses, possibly empty
   */
  List<VariableAccess> of(SyntaxNode node);
}
