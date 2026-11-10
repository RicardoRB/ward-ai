package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reports a variable or assignment that stores {@code this} under another name. */
public final class ThisAliasCheck implements RuleCheck {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final String THIS = "this";
  private static final String DECLARATION = "variableDeclaration";
  private static final String EXPRESSION = "singleExpression";
  private static final String ASSIGN = "=";

  private final boolean allowDestructuring;
  private final Set<String> allowedNames;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public ThisAliasCheck(
      final boolean allowDestructuring, final Set<String> allowedNames, final String message) {
    this.allowDestructuring = allowDestructuring;
    this.allowedNames = Set.copyOf(allowedNames);
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, DECLARATION)) {
      if (node.children().size() == SyntaxText.INFIX_PARTS
          && SyntaxText.isToken(node.children().get(1), ASSIGN)) {
        report(out, node, node.children().get(0), node.children().get(2));
      }
    }
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      if (node.children().size() == SyntaxText.INFIX_PARTS
          && SyntaxText.isToken(node.children().get(1), ASSIGN)) {
        report(out, node, node.children().get(0), node.children().get(2));
      }
    }
    return out;
  }

  private void report(
      final List<RuleMatch> out,
      final SyntaxNode node,
      final SyntaxNode target,
      final SyntaxNode value) {
    if (!THIS.equals(SyntaxText.of(value))) {
      return;
    }
    final String name = SyntaxText.of(target);
    final boolean identifier = IDENTIFIER.matcher(name).matches();
    if (identifier && allowedNames.contains(name)) {
      return;
    }
    if (!identifier && allowDestructuring) {
      return;
    }
    out.add(new RuleMatch(node.span(), message));
  }
}
