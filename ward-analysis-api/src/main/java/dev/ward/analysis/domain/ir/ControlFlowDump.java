package dev.ward.analysis.domain.ir;

import java.util.Locale;

/**
 * Renders a control-flow graph as text, one line per block, so golden fixtures and debugging share
 * one format. Each line reads {@code B<id> [<node kind>@<line>, ...] -> B<to>:<kind>}, listing
 * every node of the block in order; normal edges omit the kind. A block with no node is shown as
 * {@code entry}, {@code exit} or {@code empty}.
 */
public final class ControlFlowDump {

  private static final String ARROW = " -> ";

  private ControlFlowDump() {}

  /**
   * Renders the graph. The output is the same for the same graph, byte for byte.
   *
   * @param graph the graph to render
   * @return the header line and one line per block, each ending with a newline
   */
  public static String render(final ControlFlowGraph graph) {
    final StringBuilder out = new StringBuilder("graph ").append(graph.body()).append('\n');
    for (final BasicBlock block : graph.blocks()) {
      out.append('B').append(block.id()).append(' ').append(tag(graph, block));
      if (!block.successors().isEmpty()) {
        out.append(ARROW);
        appendSuccessors(out, block);
      }
      out.append('\n');
    }
    return out.toString();
  }

  private static String tag(final ControlFlowGraph graph, final BasicBlock block) {
    if (!block.nodes().isEmpty()) {
      final StringBuilder out = new StringBuilder("[");
      boolean first = true;
      for (final SyntaxNode node : block.nodes()) {
        if (!first) {
          out.append(", ");
        }
        first = false;
        out.append(node.kind()).append('@').append(node.span().startLine());
      }
      return out.append(']').toString();
    }
    if (block.id() == graph.entry()) {
      return "[entry]";
    }
    return block.id() == graph.exit() ? "[exit]" : "[empty]";
  }

  private static void appendSuccessors(final StringBuilder out, final BasicBlock block) {
    boolean first = true;
    for (final FlowEdge edge : block.successors()) {
      if (!first) {
        out.append(' ');
      }
      first = false;
      out.append('B').append(edge.target());
      if (edge.kind() != FlowEdgeKind.NORMAL) {
        out.append(':').append(edge.kind().name().toLowerCase(Locale.ROOT));
      }
    }
  }
}
