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
 * Reports a {@code this} or {@code super} that a path of a derived class constructor reaches before
 * the {@code super()} call, like ESLint {@code no-this-before-super}. The paths come from the
 * control-flow graph of the constructor; a nested function or class has its own {@code this}.
 */
public final class NodeThisBeforeSuperCheck implements RuleCheck {

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLASS_EXPRESSION = "classExpression";
  private static final String CLASS_HERITAGE = "classHeritage";
  private static final String CLASS_EXTENDS = "classExtendsClause";
  private static final String CLASS_TAIL = "classTail";
  private static final String EXTENDS = "'extends'";
  private static final String CALL = "singleExpression";
  private static final String CLASS_NAME = "classElementName";
  private static final String CONSTRUCTOR_DECLARATION = "constructorDeclaration";
  private static final String METHOD = "methodDefinition";
  private static final String CONSTRUCTOR_NAME = "constructor";
  private static final String THIS = "this";
  private static final String SUPER = "super";
  private static final String ARGUMENTS = "arguments";
  private static final Set<String> NESTED_SCOPES =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "constructorDeclaration",
          "propertyMemberDeclaration",
          "getAccessor",
          "setAccessor",
          CLASS_DECLARATION,
          CLASS_EXPRESSION);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeThisBeforeSuperCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
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
      return;
    }
    for (final SyntaxNode early : beforeSuper(graph.get())) {
      matches.add(new RuleMatch(early.span(), message));
    }
  }

  /**
   * The {@code this} and {@code super} tokens that a path reaches before a {@code super()} call.
   */
  private static Set<SyntaxNode> beforeSuper(final ControlFlowGraph graph) {
    final Set<SyntaxNode> early = new LinkedHashSet<>();
    final Set<String> seen = new HashSet<>();
    final Deque<int[]> work = new ArrayDeque<>();
    work.add(new int[] {graph.entry(), 0});
    while (!work.isEmpty()) {
      final int[] state = work.poll();
      if (!seen.add(state[0] + ":" + state[1])) {
        continue;
      }
      final BasicBlock block = graph.blocks().get(state[0]);
      final boolean called = runBlock(block, state[1] == 1, early);
      for (final FlowEdge edge : block.successors()) {
        if (edge.kind() != FlowEdgeKind.EXCEPTION && edge.target() != graph.exit()) {
          work.add(new int[] {edge.target(), called ? 1 : 0});
        }
      }
    }
    return early;
  }

  /** Runs the nodes of a block in order; a {@code this} before the call is early. */
  private static boolean runBlock(
      final BasicBlock block, final boolean startCalled, final Set<SyntaxNode> early) {
    boolean called = startCalled;
    for (final SyntaxNode node : block.nodes()) {
      if (!called) {
        thisTokens(node, early);
      }
      if (!SuperCalls.in(List.of(node)).isEmpty()) {
        called = true;
      }
    }
    return called;
  }

  /** The {@code this} and {@code super} tokens below a node, not counting nested scopes. */
  private static void thisTokens(final SyntaxNode node, final Set<SyntaxNode> out) {
    if (NESTED_SCOPES.contains(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      final String text = LeafText.of(node);
      if (THIS.equals(text) || SUPER.equals(text)) {
        out.add(node);
      }
      return;
    }
    final boolean call = node.children().stream().anyMatch(c -> ARGUMENTS.equals(c.kind()));
    for (final SyntaxNode child : node.children()) {
      if (!(call && SUPER.equals(LeafText.of(child)))) {
        thisTokens(child, out);
      }
    }
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
