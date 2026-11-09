package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports the constructors of derived classes that call {@code super()} on some paths only, and the
 * {@code super()} calls that a path reaches after another one, like ESLint {@code
 * constructor-super}. The paths come from the control-flow graph of the constructor, so exception
 * edges are not followed.
 */
public final class NodeConstructorSuperCheck implements RuleCheck {

  private static final String CALL = "singleExpression";
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLASS_HERITAGE = "classHeritage";
  private static final String CLASS_EXTENDS = "classExtendsClause";
  private static final String CLASS_TAIL = "classTail";
  private static final String EXTENDS = "'extends'";
  private static final String CLASS_NAME = "classElementName";
  private static final String CONSTRUCTOR_DECLARATION = "constructorDeclaration";
  private static final String METHOD = "methodDefinition";
  private static final String CONSTRUCTOR_NAME = "constructor";

  private final String message;
  private final String duplicateMessage;

  /**
   * Creates the check.
   *
   * @param message the message for a path that never calls {@code super()}
   * @param duplicateMessage the message for a {@code super()} call that follows another
   */
  public NodeConstructorSuperCheck(final String message, final String duplicateMessage) {
    this.message = Objects.requireNonNull(message, "message");
    this.duplicateMessage = Objects.requireNonNull(duplicateMessage, "duplicateMessage");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return matches(root, FlowView.NONE);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, flow, false, matches);
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node,
      final FlowView flow,
      final boolean derived,
      final List<RuleMatch> matches) {
    final boolean inDerived = isClass(node) ? extendsSomething(node) : derived;
    if (inDerived && isConstructor(node)) {
      checkConstructor(node, flow, matches);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, flow, inDerived, matches);
    }
  }

  private static boolean isClass(final SyntaxNode node) {
    return CLASS_DECLARATION.equals(node.kind())
        || (CALL.equals(node.kind())
            && node.children().stream().anyMatch(c -> "'class'".equals(c.kind())));
  }

  /** A class extends a base when its heritage has an extends clause, or its tail has extends. */
  private static boolean extendsSomething(final SyntaxNode node) {
    return node.children().stream()
        .anyMatch(
            child ->
                (CLASS_HERITAGE.equals(child.kind())
                        && child.children().stream().anyMatch(c -> CLASS_EXTENDS.equals(c.kind())))
                    || (CLASS_TAIL.equals(child.kind())
                        && child.children().stream().anyMatch(c -> EXTENDS.equals(c.kind()))));
  }

  private static boolean isConstructor(final SyntaxNode node) {
    return CONSTRUCTOR_DECLARATION.equals(node.kind())
        || METHOD.equals(node.kind())
            && node.children().stream()
                .anyMatch(
                    c -> CLASS_NAME.equals(c.kind()) && CONSTRUCTOR_NAME.equals(firstLeafText(c)));
  }

  private void checkConstructor(
      final SyntaxNode constructor, final FlowView flow, final List<RuleMatch> matches) {
    final Optional<ControlFlowGraph> graph = NodeFlow.graphOf(flow, constructor);
    if (graph.isEmpty()) {
      if (SuperCalls.in(constructor.children()).isEmpty()) {
        matches.add(new RuleMatch(constructor.span(), message));
      }
      return;
    }
    final Paths paths = paths(graph.get());
    if (paths.missing) {
      matches.add(new RuleMatch(constructor.span(), message));
    }
    for (final SyntaxNode duplicate : paths.duplicates) {
      matches.add(new RuleMatch(duplicate.span(), duplicateMessage));
    }
  }

  /** Whether some path reaches the exit without {@code super()}, and the repeated calls. */
  private record Paths(boolean missing, List<SyntaxNode> duplicates) {}

  /**
   * Walks the graph with one flag: whether a {@code super()} call has run on the path so far. A
   * call made while the flag is set is a duplicate; a path that reaches the exit with the flag
   * clear is missing the call.
   */
  private static Paths paths(final ControlFlowGraph graph) {
    boolean missing = false;
    final Set<SyntaxNode> duplicates = new LinkedHashSet<>();
    final Set<String> seen = new HashSet<>();
    final Deque<int[]> work = new ArrayDeque<>();
    work.add(new int[] {graph.entry(), 0});
    while (!work.isEmpty()) {
      final int[] state = work.poll();
      if (!seen.add(state[0] + ":" + state[1])) {
        continue;
      }
      final BasicBlock block = graph.blocks().get(state[0]);
      final boolean called = callBlock(block, state[1] == 1, duplicates);
      missing |= leaveBlock(graph, block, called, work);
    }
    return new Paths(missing, new ArrayList<>(duplicates));
  }

  /** Runs the super calls of a block; returns whether a call has run after the block. */
  private static boolean callBlock(
      final BasicBlock block, final boolean startCalled, final Set<SyntaxNode> duplicates) {
    boolean called = startCalled;
    for (final SyntaxNode node : block.nodes()) {
      for (final SyntaxNode call : SuperCalls.in(List.of(node))) {
        if (called) {
          duplicates.add(call);
        }
        called = true;
      }
    }
    return called;
  }

  /**
   * Queues the successors of a block with the flag; returns whether the exit is reached without a
   * call.
   */
  private static boolean leaveBlock(
      final ControlFlowGraph graph,
      final BasicBlock block,
      final boolean called,
      final Deque<int[]> work) {
    boolean missing = false;
    for (final FlowEdge edge : block.successors()) {
      if (edge.kind() == FlowEdgeKind.EXCEPTION) {
        continue;
      }
      if (edge.target() == graph.exit()) {
        missing |= !called;
      } else {
        work.add(new int[] {edge.target(), called ? 1 : 0});
      }
    }
    return missing;
  }

  private static String firstLeafText(final SyntaxNode node) {
    SyntaxNode current = node;
    while (!current.children().isEmpty()) {
      current = current.children().get(0);
    }
    final String text = current.fields().get("text");
    return text == null ? "" : text;
  }
}
