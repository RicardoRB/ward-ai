package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;

/** Walks a syntax subtree in evaluation order, adding the accesses it makes to the list. */
@FunctionalInterface
interface NodeWalker {

  /**
   * Adds the accesses of a subtree.
   *
   * @param node the root of the subtree
   * @param found the list the accesses are added to, in evaluation order
   */
  void walk(SyntaxNode node, List<VariableAccess> found);
}
