package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports a derived class constructor that reaches its end, or reads {@code this}, on a code path
 * that has not called {@code super()}. The finding sits on the constructor. A path that leaves the
 * body by an exception is not a return and is left alone. shortcut: a second super() call on one
 * path is not reported
 */
public final class BiomeUnreachableSuperCheck implements RuleCheck {

  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String METHOD = "methodDefinition";
  private static final String CLASS_TAIL = "classTail";
  private static final String NAME = "classElementName";
  private static final String EXTENDS_TOKEN = "'extends'";
  private static final String CLASS = "classDeclaration";
  private static final String HERITAGE = "classHeritage";
  private static final String EXTENDS = "classExtendsClause";
  private static final String EXPRESSION = "singleExpression";
  private static final String SUPER = "super";
  private static final String THIS = "this";
  private static final String ARGUMENTS = "arguments";
  private static final Set<String> NESTED =
      Set.of(
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "functionDeclaration",
          CONSTRUCTOR,
          METHOD,
          "propertyMemberDeclaration",
          CLASS);
  private static final Set<FlowEdgeKind> RETURNING =
      EnumSet.of(FlowEdgeKind.NORMAL, FlowEdgeKind.TRUE, FlowEdgeKind.FALSE);
  private static final int NOT_CALLED = 1;
  private static final int CALLED = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeUnreachableSuperCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the control-flow graphs, which only {@link #matches(SyntaxNode,
   * FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unreachable-super check needs the control-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final Map<Span, ControlFlowGraph> bySpan = new HashMap<>();
    flow.graphs().forEach(graph -> bySpan.put(graph.span(), graph));
    return SyntaxWalk.visits(root, Set.of(CONSTRUCTOR, METHOD)).stream()
        .filter(visit -> isConstructor(visit.node()))
        .filter(visit -> derived(visit.ancestors()))
        .filter(visit -> bySpan.containsKey(visit.node().span()))
        .filter(visit -> missesSuper(bySpan.get(visit.node().span())))
        .map(visit -> new RuleMatch(visit.node().span(), message))
        .sorted(Comparator.comparingInt(match -> match.span().startOffset()))
        .toList();
  }

  /** A method that is a constructor: a constructor declaration, or a method named constructor. */
  private static boolean isConstructor(final SyntaxNode node) {
    return CONSTRUCTOR.equals(node.kind())
        || METHOD.equals(node.kind())
            && node.children().stream()
                .filter(child -> NAME.equals(child.kind()))
                .anyMatch(name -> "constructor".equals(SyntaxWalk.text(name)));
  }

  /** The nearest enclosing class lists an {@code extends} clause, in either grammar's shape. */
  private static boolean derived(final List<SyntaxNode> ancestors) {
    for (int level = ancestors.size() - 1; level >= 0; level--) {
      final SyntaxNode node = ancestors.get(level);
      if (CLASS.equals(node.kind())) {
        return node.children().stream().anyMatch(BiomeUnreachableSuperCheck::extendsClause);
      }
    }
    return false;
  }

  private static boolean extendsClause(final SyntaxNode part) {
    return HERITAGE.equals(part.kind()) && BiomeTree.hasChild(part, EXTENDS)
        || CLASS_TAIL.equals(part.kind()) && BiomeTree.hasChild(part, EXTENDS_TOKEN);
  }

  /** The result of one block: whether it reads this before super, and the state it leaves. */
  private record Pass(boolean thisBeforeSuper, int state) {}

  /** A path reads {@code this} before {@code super()}, or reaches the end without calling it. */
  private static boolean missesSuper(final ControlFlowGraph graph) {
    final Map<Integer, Integer> entering = new HashMap<>();
    final Map<Integer, BasicBlock> blocks = new HashMap<>();
    graph.blocks().forEach(block -> blocks.put(block.id(), block));
    final Deque<Integer> pending = new ArrayDeque<>();
    entering.put(graph.entry(), NOT_CALLED);
    pending.add(graph.entry());
    boolean thisFirst = false;
    while (!pending.isEmpty()) {
      final int id = pending.poll();
      final Pass pass = run(blocks.get(id), entering.get(id));
      thisFirst |= pass.thisBeforeSuper();
      propagate(blocks.get(id), pass.state(), entering, pending);
    }
    final boolean endsUncalled = (entering.getOrDefault(graph.exit(), 0) & NOT_CALLED) != 0;
    return thisFirst || endsUncalled;
  }

  private static Pass run(final BasicBlock block, final int entry) {
    boolean thisFirst = false;
    int state = entry;
    for (final SyntaxNode node : block.nodes()) {
      for (final String event : eventsOf(node)) {
        if (THIS.equals(event) && (state & NOT_CALLED) != 0) {
          thisFirst = true;
        } else if (SUPER.equals(event)) {
          state = CALLED;
        }
      }
    }
    return new Pass(thisFirst, state);
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
        && isKeyword(parts.get(0), "super");
  }

  private static boolean isThis(final SyntaxNode node) {
    return isKeyword(node, "this");
  }

  /** A single-part expression whose whole text is the keyword, in either grammar's shape. */
  private static boolean isKeyword(final SyntaxNode node, final String keyword) {
    return EXPRESSION.equals(node.kind())
        && node.children().size() == 1
        && keyword.equals(SyntaxWalk.text(node));
  }
}
