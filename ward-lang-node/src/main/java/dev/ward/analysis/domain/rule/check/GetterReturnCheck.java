package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports getters that never return a value, like ESLint {@code getter-return}: a getter with no
 * {@code return} at all, and, unless {@code allowImplicit} is set, a bare {@code return;}. A getter
 * is a node with a direct {@code getter} child: a class, object literal or TypeScript accessor.
 * Nested functions are not searched.
 *
 * <p>shortcut: reachability is not analysed, so a getter that returns on only some paths is not
 * reported, upgrade with the control-flow graphs when a fixture needs it.
 */
public final class GetterReturnCheck implements RuleCheck {

  private static final String GETTER = "getter";
  private static final String RETURN = "returnStatement";
  private static final String ARGUMENT = "expressionSequence";
  private static final Set<String> NESTED =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "propertyAssignment",
          "getAccessor",
          "setAccessor");

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
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (isGetter(node)) {
      checkGetter(node, matches);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private static boolean isGetter(final SyntaxNode node) {
    return node.children().stream().anyMatch(child -> GETTER.equals(child.kind()));
  }

  private void checkGetter(final SyntaxNode getter, final List<RuleMatch> matches) {
    final List<SyntaxNode> returns = new ArrayList<>();
    returnsIn(getter.children(), returns);
    if (returns.isEmpty()) {
      matches.add(new RuleMatch(getter.span(), message));
    }
    if (!allowImplicit) {
      returns.stream()
          .filter(statement -> statement.children().stream().noneMatch(c -> ARGUMENT.equals(c.kind())))
          .forEach(statement -> matches.add(new RuleMatch(statement.span(), message)));
    }
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
