package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each {@code if}, {@code for}, {@code while} and {@code do} statement whose body is not a
 * block. The body of an {@code else} may be another {@code if}. The decision reads the statement's
 * syntax nodes, never its text.
 */
public final class ControlBodyBracesCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String BLOCK = "block";
  private static final String IF = "'if'";
  private static final String ELSE = "'else'";
  private static final Set<String> CONTROLS = Set.of(IF, "'for'", "'while'", "'do'");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public ControlBodyBracesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(ControlBodyBracesCheck::hasBareBody)
        .map(node -> CheckSupport.at(node.span(), CheckSupport.fill(message, "", "")))
        .toList();
  }

  private static boolean hasBareBody(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.isEmpty() || !CONTROLS.contains(children.get(0).kind())) {
      return false;
    }
    final boolean elseIf = IF.equals(children.get(0).kind());
    for (int index = 0; index < children.size(); index++) {
      if (STATEMENT.equals(children.get(index).kind()) && !isAllowed(children, index, elseIf)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAllowed(
      final List<SyntaxNode> children, final int index, final boolean elseIf) {
    final SyntaxNode body = children.get(index);
    final List<SyntaxNode> parts = body.children();
    final boolean block = !parts.isEmpty() && BLOCK.equals(parts.get(0).kind());
    return block
        || elseIf
            && index > 0
            && ELSE.equals(children.get(index - 1).kind())
            && !parts.isEmpty()
            && IF.equals(parts.get(0).kind());
  }
}
