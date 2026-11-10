package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reports a call, a bracket access or a tagged template whose opening token starts on a later line
 * than the expression before it, like ESLint {@code no-unexpected-multiline}. The token is the
 * opening parenthesis, the bracket or the template, as ESLint reports it.
 */
public final class NodeUnexpectedMultilineCheck implements RuleCheck {

  private static final String ACCESS = "singleExpression";
  private static final String ARGUMENTS = "arguments";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String OPEN_BRACKET = "[";
  private static final String CALL_MESSAGE =
      "unexpected newline between function and ( of function call";
  private static final String BRACKET_MESSAGE =
      "unexpected newline between object and [ of property access";
  private static final String TEMPLATE_MESSAGE =
      "unexpected newline between template tag and template literal";
  private static final int BRACKET_CHILDREN = 4;
  private static final int BRACKET_INDEX = 1;

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (ACCESS.equals(node.kind())) {
      check(node, matches);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private static void check(final SyntaxNode node, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = node.children();
    if (children.size() == 2 && ARGUMENTS.equals(children.get(1).kind())) {
      firstChild(children.get(1))
          .filter(open -> startsLater(children.get(0), open))
          .ifPresent(open -> matches.add(new RuleMatch(open.span(), CALL_MESSAGE)));
    } else if (children.size() == 2 && TEMPLATE.equals(children.get(1).kind())) {
      if (startsLater(children.get(0), children.get(1))) {
        matches.add(new RuleMatch(children.get(1).span(), TEMPLATE_MESSAGE));
      }
    } else if (children.size() == BRACKET_CHILDREN
        && OPEN_BRACKET.equals(LeafText.of(children.get(BRACKET_INDEX)))
        && startsLater(children.get(0), children.get(BRACKET_INDEX))) {
      matches.add(new RuleMatch(children.get(BRACKET_INDEX).span(), BRACKET_MESSAGE));
    }
  }

  private static Optional<SyntaxNode> firstChild(final SyntaxNode node) {
    return node.children().isEmpty() ? Optional.empty() : Optional.of(node.children().get(0));
  }

  /** Reports whether the token starts on a line after the one where the previous part ends. */
  private static boolean startsLater(final SyntaxNode before, final SyntaxNode token) {
    return token.span().startLine() > before.span().endLine();
  }
}
