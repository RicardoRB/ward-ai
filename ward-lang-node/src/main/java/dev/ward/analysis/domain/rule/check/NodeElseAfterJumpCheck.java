package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each {@code else} whose {@code if} branch always returns or throws, like ESLint's
 * no-else-return. An {@code else if} is reported only when {@code allowElseIf} is false.
 */
public final class NodeElseAfterJumpCheck implements RuleCheck {

  private static final String ELSE = "'else'";
  private static final String IF = "ifStatement";
  private static final Set<String> WRAPPERS = Set.of("statement", "block", "statementList");
  private static final Set<String> JUMPS = Set.of("returnStatement", "throwStatement");

  private final boolean allowElseIf;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowElseIf whether an {@code else if} after a jump is allowed
   * @param message the message of each finding
   */
  public NodeElseAfterJumpCheck(final boolean allowElseIf, final String message) {
    this.allowElseIf = allowElseIf;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (IF.equals(node.kind())) {
      report(node).ifPresent(matches::add);
    }
    node.children().forEach(child -> collect(child, matches));
  }

  private Optional<RuleMatch> report(final SyntaxNode ifStatement) {
    final List<SyntaxNode> parts = NodeSyntax.code(ifStatement);
    final int elseAt = indexOf(parts, ELSE);
    if (elseAt < 1 || elseAt + 1 >= parts.size()) {
      return Optional.empty();
    }
    final boolean elseIf = IF.equals(onlyRule(parts.get(elseAt + 1)).kind());
    if ((elseIf && allowElseIf) || !alwaysJumps(parts.get(elseAt - 1))) {
      return Optional.empty();
    }
    return Optional.of(new RuleMatch(parts.get(elseAt).span(), message));
  }

  private static int indexOf(final List<SyntaxNode> parts, final String kind) {
    for (int i = 0; i < parts.size(); i++) {
      if (kind.equals(parts.get(i).kind())) {
        return i;
      }
    }
    return -1;
  }

  /** Descends through wrappers that hold exactly one rule, such as a {@code statement}. */
  private static SyntaxNode onlyRule(final SyntaxNode node) {
    SyntaxNode current = node;
    while (WRAPPERS.contains(current.kind()) && rules(current).size() == 1) {
      current = rules(current).get(0);
    }
    return current;
  }

  /** Whether the statement always ends in a return or throw, judged on its last rule. */
  private static boolean alwaysJumps(final SyntaxNode statement) {
    if (JUMPS.contains(statement.kind())) {
      return true;
    }
    if (!WRAPPERS.contains(statement.kind())) {
      return false;
    }
    final List<SyntaxNode> rules = rules(statement);
    return !rules.isEmpty() && alwaysJumps(rules.get(rules.size() - 1));
  }

  /** The children that are rules, not tokens: a token is a leaf with no children. */
  private static List<SyntaxNode> rules(final SyntaxNode node) {
    return NodeSyntax.code(node).stream().filter(child -> !child.children().isEmpty()).toList();
  }
}
