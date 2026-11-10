package dev.ward.analysis.domain.ir;

import java.util.List;
import java.util.Objects;

/**
 * The control flow of one executable body: a method, a constructor, a function, an arrow function,
 * a lambda, or the top-level statements of a script or module.
 *
 * @param body the name of the body, for example {@code Type#method(2)} or {@code <module>}
 * @param span where the body is in the source
 * @param blocks the basic blocks, indexed by their id
 * @param entry the index of the entry block
 * @param exit the index of the exit block
 */
public record ControlFlowGraph(
    String body, Span span, List<BasicBlock> blocks, int entry, int exit) {

  /**
   * Copies the blocks and checks that the entry and exit are two blocks of the graph.
   *
   * @throws IllegalArgumentException if the entry or exit is not a block index, or they are equal
   * @throws NullPointerException if a required value is missing
   */
  public ControlFlowGraph {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(span, "span");
    blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    if (entry == exit || !isBlock(blocks, entry) || !isBlock(blocks, exit)) {
      throw new IllegalArgumentException("entry and exit must be two distinct blocks");
    }
  }

  private static boolean isBlock(final List<BasicBlock> blocks, final int index) {
    return index >= 0 && index < blocks.size() && blocks.get(index).id() == index;
  }
}
