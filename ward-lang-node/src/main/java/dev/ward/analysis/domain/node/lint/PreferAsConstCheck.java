package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reports a literal type that repeats a literal value, where {@code as const} would do. */
public final class PreferAsConstCheck implements RuleCheck {

  private static final Pattern LITERAL =
      Pattern.compile("'[^']*'|\"[^\"]*\"|-?[0-9][0-9_.]*|true|false");
  private static final String AS = "as";
  private static final String ASSIGN = "=";
  private static final String EXPRESSION = "singleExpression";
  private static final String DECLARATION = "variableDeclaration";
  private static final String ANNOTATION = "typeAnnotation";

  private final String message;

  /** Creates the check with the message of the rule. */
  public PreferAsConstCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == 3 && SyntaxText.isToken(parts.get(1), AS)) {
        checkPair(out, parts.get(0), parts.get(2));
      }
    }
    for (final SyntaxNode node : SyntaxText.nodesOf(root, DECLARATION)) {
      checkDeclaration(out, node);
    }
    return out;
  }

  private void checkDeclaration(final List<RuleMatch> out, final SyntaxNode node) {
    final List<SyntaxNode> annotations = SyntaxText.childrenOf(node, ANNOTATION);
    final SyntaxNode init = SyntaxText.last(node);
    if (annotations.size() == 1 && init != null && node.children().size() == 4) {
      final SyntaxNode type = SyntaxText.last(annotations.get(0));
      if (type != null && isSameLiteral(init, type)) {
        out.add(new RuleMatch(type.span(), message));
      }
    }
  }

  private void checkPair(final List<RuleMatch> out, final SyntaxNode value, final SyntaxNode type) {
    if (isSameLiteral(value, type)) {
      out.add(new RuleMatch(type.span(), message));
    }
  }

  private static boolean isSameLiteral(final SyntaxNode value, final SyntaxNode type) {
    final String text = SyntaxText.of(value);
    return LITERAL.matcher(text).matches() && text.equals(SyntaxText.of(type));
  }
}
