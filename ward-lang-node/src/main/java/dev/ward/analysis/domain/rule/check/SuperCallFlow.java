package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The control-flow walk of {@link BiomeUnreachableSuperCheck}: whether a constructor's graph has a
 * path that reads {@code this} before {@code super()}, calls it again, or returns without calling
 * it.
 */
final class SuperCallFlow {

  private static final String EXPRESSION = "singleExpression";
  private static final String ARGUMENTS = "arguments";
  private static final String SUPER = "super";
  private static final String THIS = "this";
  private static final Set<String> NESTED =
      Set.of(
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "functionDeclaration",
          "constructorDeclaration",
          "methodDefinition",
          "propertyMemberDeclaration",
          "classDeclaration");
  private static final Set<FlowEdgeKind> RETURNING =
      EnumSet.of(FlowEdgeKind.NORMAL, FlowEdgeKind.TRUE, FlowEdgeKind.FALSE);
  private static final int NOT_CALLED = 1;
  private static final int CALLED = 2;

  private SuperCallFlow() {}

  /** The result of one block: whether a path breaks the rule there, and the state it leaves. */
  private record Pass(boolean violation, int state) {}

  /**
   * Tells whether a path breaks the rule: it reads {@code this} before {@code super()}, calls
   * {@code super()} again, or reaches the end without calling it.
   */
  static boolean misses(final ControlFlowGraph graph) {
    final Map<Integer, Integer> entering = new HashMap<>();
    final Map<Integer, BasicBlock> blocks = new HashMap<>();
    graph.blocks().forEach(block -> blocks.put(block.id(), block));
    final Deque<Integer> pending = new ArrayDeque<>();
    entering.put(graph.entry(), NOT_CALLED);
    pending.add(graph.entry());
    boolean violation = false;
    while (!pending.isEmpty()) {
      final int id = pending.poll();
      final Pass pass = run(blocks.get(id), entering.get(id));
      violation |= pass.violation();
      propagate(blocks.get(id), pass.state(), entering, pending);
    }
    final boolean endsUncalled = (entering.getOrDefault(graph.exit(), 0) & NOT_CALLED) != 0;
    return violation || endsUncalled;
  }

  /** Runs the events of a block: this before super, and super after super, break the rule. */
  private static Pass run(final BasicBlock block, final int entry) {
    boolean violation = false;
    int state = entry;
    for (final SyntaxNode node : block.nodes()) {
      for (final String event : eventsOf(node)) {
        violation |= breaks(event, state);
        if (SUPER.equals(event)) {
          state = CALLED;
        }
      }
    }
    return new Pass(violation, state);
  }

  private static boolean breaks(final String event, final int state) {
    return THIS.equals(event) && (state & NOT_CALLED) != 0
        || SUPER.equals(event) && (state & CALLED) != 0;
  }

  /** Merges the state into each block the block returns to, and queues the ones that change. */
  private static void propagate(
      final BasicBlock block,
      final int state,
      final Map<Integer, Integer> entering,
      final Deque<Integer> pending) {
    for (final FlowEdge edge : block.successors()) {
      if (RETURNING.contains(edge.kind())) {
        final int before = entering.getOrDefault(edge.target(), 0);
        final int merged = before | state;
        if (merged != before) {
          entering.put(edge.target(), merged);
          pending.add(edge.target());
        }
      }
    }
  }

  private static List<String> eventsOf(final SyntaxNode node) {
    final List<String> events = new ArrayList<>();
    events(node, events);
    return events;
  }

  /** Collects the super calls and this reads of a node in evaluation order, not in nested code. */
  private static void events(final SyntaxNode node, final List<String> events) {
    if (NESTED.contains(node.kind())) {
      return;
    }
    for (final SyntaxNode child : node.children()) {
      events(child, events);
    }
    if (isSuperCall(node)) {
      events.add(SUPER);
    } else if (isThis(node)) {
      events.add(THIS);
    }
  }

  private static boolean isSuperCall(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return EXPRESSION.equals(node.kind())
        && parts.size() == 2
        && BiomeTree.isKind(parts.get(1), ARGUMENTS)
        && isKeyword(parts.get(0), SUPER);
  }

  private static boolean isThis(final SyntaxNode node) {
    return isKeyword(node, THIS);
  }

  /** A single-part expression whose whole text is the keyword, in either grammar's shape. */
  private static boolean isKeyword(final SyntaxNode node, final String keyword) {
    return EXPRESSION.equals(node.kind())
        && node.children().size() == 1
        && keyword.equals(SyntaxWalk.text(node));
  }
}
