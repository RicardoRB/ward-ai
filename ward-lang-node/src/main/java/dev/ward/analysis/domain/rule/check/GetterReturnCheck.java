package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports getters that can finish without returning a value, like ESLint {@code getter-return}: a
 * getter whose body end is reachable is reported at its head, and, unless {@code allowImplicit} is
 * set, a bare {@code return;} is reported. A getter is a node with a direct {@code getter} child: a
 * class, object literal or TypeScript accessor. Nested functions are not searched.
 *
 * <p>Without a control-flow graph (a body that could not be lowered), a getter with no return at
 * all is reported instead.
 */
public final class GetterReturnCheck implements RuleCheck {

  private static final String GETTER = "getter";
  private static final String RETURN = "returnStatement";
  private static final String ARGUMENT = "expressionSequence";
  private static final Set<String> NESTED = NodeKinds.NESTED_BODIES;

  private final boolean allowImplicit;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowImplicit whether a bare {@code return;} is accepted
   * @param message the message of each finding
   */
  public GetterReturnCheck(final boolean allowImplicit, final String message) {
    this.allowImplicit = allowImplicit;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return matches(root, FlowView.NONE);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, flow, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final FlowView flow, final List<RuleMatch> matches) {
    if (isGetter(node)) {
      checkGetter(node, flow, matches);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, flow, matches);
    }
  }

  private static boolean isGetter(final SyntaxNode node) {
    return node.children().stream().anyMatch(child -> GETTER.equals(child.kind()));
  }

  private void checkGetter(
      final SyntaxNode getter, final FlowView flow, final List<RuleMatch> matches) {
    final List<SyntaxNode> returns = new ArrayList<>();
    returnsIn(getter.children(), returns);
    if (endReachable(getter, flow, returns)) {
      matches.add(new RuleMatch(getter.span(), message));
    }
    if (!allowImplicit) {
      returns.stream()
          .filter(
              statement -> statement.children().stream().noneMatch(c -> ARGUMENT.equals(c.kind())))
          .forEach(statement -> matches.add(new RuleMatch(statement.span(), message)));
    }
  }

  /** Reachability decides when the body has a graph; a getter with no return is the fallback. */
  private static boolean endReachable(
      final SyntaxNode getter, final FlowView flow, final List<SyntaxNode> returns) {
    final Optional<dev.ward.analysis.domain.ir.ControlFlowGraph> graph =
        NodeFlow.graphOf(flow, getter);
    return graph.map(NodeFlow::fallsOffEnd).orElseGet(returns::isEmpty);
  }

  private static void returnsIn(final List<SyntaxNode> nodes, final List<SyntaxNode> returns) {
    for (final SyntaxNode node : nodes) {
      if (RETURN.equals(node.kind())) {
        returns.add(node);
      }
      if (!NESTED.contains(node.kind())) {
        returnsIn(node.children(), returns);
      }
    }
  }
}
