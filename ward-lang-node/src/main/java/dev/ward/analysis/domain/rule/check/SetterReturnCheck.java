package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports a {@code return} with a value in a setter, like ESLint {@code no-setter-return}. A nested
 * function, arrow function or class inside the setter has its own returns, so they are not
 * reported.
 */
public final class SetterReturnCheck implements RuleCheck {

  private static final String SETTER = "setter";
  private static final String SET_ACCESSOR = "setAccessor";
  private static final String RETURN = "returnStatement";
  private static final String ARGUMENT = "expressionSequence";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SetterReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (isSetter(node)) {
      final List<SyntaxNode> returns = new ArrayList<>();
      returnsIn(node.children(), returns);
      returns.stream()
          .filter(
              statement -> statement.children().stream().anyMatch(c -> ARGUMENT.equals(c.kind())))
          .forEach(statement -> matches.add(new RuleMatch(statement.span(), message)));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private static boolean isSetter(final SyntaxNode node) {
    return SET_ACCESSOR.equals(node.kind())
        || node.children().stream().anyMatch(child -> SETTER.equals(child.kind()));
  }

  private static void returnsIn(final List<SyntaxNode> nodes, final List<SyntaxNode> returns) {
    for (final SyntaxNode node : nodes) {
      if (RETURN.equals(node.kind())) {
        returns.add(node);
      }
      if (!NodeKinds.NESTED_BODIES.contains(node.kind())) {
        returnsIn(node.children(), returns);
      }
    }
  }
}
