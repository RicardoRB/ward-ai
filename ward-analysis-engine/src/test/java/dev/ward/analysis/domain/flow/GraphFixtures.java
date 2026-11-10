package dev.ward.analysis.domain.flow;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds small control-flow graphs by hand. Block 0 is the entry and block 1 the exit; the blocks
 * that follow are numbered in the order given. A variable is declared at a span that depends only
 * on its name, so two accesses of one name touch one variable.
 */
public final class GraphFixtures {

  private static final int LINE_OFFSET = 100;
  private static final int NAME_MASK = 0xFFFF;

  private GraphFixtures() {}

  /** One block: its variable accesses and its outgoing edges. */
  public static final class Block {

    private final List<VariableAccess> accesses = new ArrayList<>();
    private final List<FlowEdge> edges = new ArrayList<>();

    /** Adds a write of the variable on the line. */
    public Block write(final String name, final int line) {
      return add(AccessKind.WRITE, name, line);
    }

    /** Adds a read of the variable on the line. */
    public Block read(final String name, final int line) {
      return add(AccessKind.READ, name, line);
    }

    /** Adds the incoming value of a parameter on the line. */
    public Block parameter(final String name, final int line) {
      return add(AccessKind.PARAMETER, name, line);
    }

    /** Adds a capture of the variable by a nested body on the line. */
    public Block capture(final String name, final int line) {
      return add(AccessKind.CAPTURE, name, line);
    }

    /** Adds a normal edge to the target block. */
    public Block edge(final int target) {
      edges.add(new FlowEdge(target, FlowEdgeKind.NORMAL));
      return this;
    }

    /** Adds an exception edge to the target block. */
    public Block exception(final int target) {
      edges.add(new FlowEdge(target, FlowEdgeKind.EXCEPTION));
      return this;
    }

    private Block add(final AccessKind kind, final String name, final int line) {
      accesses.add(new VariableAccess(name, kind, span(line), declared(name)));
      return this;
    }
  }

  /** Builds the graph whose blocks are the given ones, in order. */
  public static ControlFlowGraph graph(final Block... blocks) {
    final List<BasicBlock> built = new ArrayList<>();
    for (int id = 0; id < blocks.length; id++) {
      built.add(new BasicBlock(id, List.of(), blocks[id].edges, blocks[id].accesses));
    }
    return new ControlFlowGraph("g", span(1), built, 0, 1);
  }

  /** An empty block, for the entry and the exit. */
  public static Block empty() {
    return new Block();
  }

  /** The span of an access on a line, unique to that line. */
  static Span span(final int line) {
    return new Span(line, 1, line, 2, line * LINE_OFFSET, line * LINE_OFFSET + 1);
  }

  /** The declaration of a variable, which depends only on its name. */
  static Span declared(final String name) {
    final int offset = name.hashCode() & NAME_MASK;
    return new Span(1, 1, 1, 2, offset, offset + 1);
  }
}
