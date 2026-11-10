package dev.ward.analysis.domain.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the blocks of one body while a language lowering walks its syntax tree. Blocks are
 * numbered in creation order: block 0 is the entry and block 1 the exit. Jumps and try statements
 * are lowered through the views returned by {@link #jumps()} and {@link #tries()}, which share one
 * stack of enclosing frames.
 */
public final class ControlFlowBuilder {

  /** The index of the entry block. */
  public static final int ENTRY = 0;

  /** The index of the exit block. */
  public static final int EXIT = 1;

  private final List<List<SyntaxNode>> nodes = new ArrayList<>();
  private final List<List<FlowEdge>> edges = new ArrayList<>();
  private final List<List<VariableAccess>> accesses = new ArrayList<>();
  private final List<List<FlowSite>> sites = new ArrayList<>();
  private final VariableAccesses finder;
  private final FlowSites recorder;
  private final ControlFlowFrames frames = new ControlFlowFrames(this);
  private int current;

  /** Starts a body that records no variable accesses or flow sites. */
  public ControlFlowBuilder() {
    this(VariableAccesses.NONE, FlowSites.NONE);
  }

  /**
   * Starts a body that records no flow sites.
   *
   * @param finder finds the variable accesses of each node added
   */
  public ControlFlowBuilder(final VariableAccesses finder) {
    this(finder, FlowSites.NONE);
  }

  /**
   * Starts a body with its entry and exit blocks; the entry is the current block.
   *
   * @param finder finds the variable accesses of each node added
   * @param recorder finds the flow sites of each node added
   */
  public ControlFlowBuilder(final VariableAccesses finder, final FlowSites recorder) {
    this.finder = Objects.requireNonNull(finder, "finder");
    this.recorder = Objects.requireNonNull(recorder, "recorder");
    newBlock();
    newBlock();
    current = ENTRY;
  }

  /**
   * Creates an empty block.
   *
   * @return the index of the new block
   */
  public int newBlock() {
    nodes.add(new ArrayList<>());
    edges.add(new ArrayList<>());
    accesses.add(new ArrayList<>());
    sites.add(new ArrayList<>());
    return nodes.size() - 1;
  }

  /**
   * Returns the block that receives the next statement.
   *
   * @return the index of the current block
   */
  public int current() {
    return current;
  }

  /**
   * Makes a block the current one.
   *
   * @param block the index of the block
   */
  public void moveTo(final int block) {
    current = block;
  }

  /**
   * Appends a statement or condition node to the current block. When the node can throw and a try
   * encloses it, the current block also gets an exception edge to that try's handler.
   *
   * @param node the statement or condition node
   * @param mayThrow whether the node contains a call, an allocation, a throw or an await
   */
  public void add(final SyntaxNode node, final boolean mayThrow) {
    nodes.get(current).add(node);
    accesses.get(current).addAll(finder.of(node));
    sites.get(current).addAll(recorder.of(node));
    if (mayThrow && frames.catchesRaise()) {
      frames.propagate(current, FlowEdgeKind.EXCEPTION);
    }
  }

  /**
   * Appends variable accesses and flow sites to the current block, after the nodes already added. A
   * lowering uses this for accesses and sites that no node carries, such as the parameters at the
   * entry of a body.
   *
   * @param found the accesses, in evaluation order
   * @param recorded the flow sites, in evaluation order
   */
  public void record(final List<VariableAccess> found, final List<FlowSite> recorded) {
    accesses.get(current).addAll(found);
    sites.get(current).addAll(recorded);
  }

  /**
   * Adds an edge between two blocks. An edge that already exists is not added again.
   *
   * @param from the source block
   * @param to the destination block
   * @param kind why control moves
   */
  public void edge(final int from, final int to, final FlowEdgeKind kind) {
    final FlowEdge edge = new FlowEdge(to, kind);
    final List<FlowEdge> outgoing = edges.get(from);
    if (!outgoing.contains(edge)) {
      outgoing.add(edge);
    }
  }

  /**
   * Splits the current block on a condition: a {@code true} edge to one block and a {@code false}
   * edge to another. The current block does not move.
   *
   * @param ifTrue the block that runs when the condition holds
   * @param ifFalse the block that runs when it does not
   */
  public void branch(final int ifTrue, final int ifFalse) {
    edge(current, ifTrue, FlowEdgeKind.TRUE);
    edge(current, ifFalse, FlowEdgeKind.FALSE);
  }

  /**
   * Finishes the body: the current block falls through to the exit. Blocks that no path from the
   * entry reaches and that hold no node are dropped; see {@link ControlFlowCompaction}.
   *
   * @param body the name of the body
   * @param span where the body is in the source
   * @return the graph of the body
   */
  public ControlFlowGraph build(final String body, final Span span) {
    edge(current, EXIT, FlowEdgeKind.NORMAL);
    return new ControlFlowGraph(
        body, span, ControlFlowCompaction.compact(nodes, edges, accesses, sites), ENTRY, EXIT);
  }

  /**
   * The jumps of this body: loops, switches, labels, and the transfers that leave them.
   *
   * @return a view over this builder's frames
   */
  public ControlFlowJumps jumps() {
    return new ControlFlowJumps(frames);
  }

  /**
   * The try statements of this body and the exits that leave them.
   *
   * @return a view over this builder's frames
   */
  public ControlFlowTries tries() {
    return new ControlFlowTries(this, frames);
  }
}
