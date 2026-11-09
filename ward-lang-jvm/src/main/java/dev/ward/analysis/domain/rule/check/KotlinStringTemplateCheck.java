package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a template entry of a single-line string that ktlint's {@code string-template} rule
 * simplifies: {@code ${name}} where the braces are not needed, and {@code ${name.toString()}},
 * whose call is redundant. Multi-line strings are not checked.
 */
public final class KotlinStringTemplateCheck implements RuleCheck {

  private static final String ENTRY = "lineStringExpression";
  private static final String EXPRESSION = "expression";
  private static final String CONTENT = "lineStringContent";
  private static final String TEXT = "LineStrText";
  private static final String CLOSING_QUOTE = "'\"'";
  private static final Pattern IDENTIFIER_OR_THIS = Pattern.compile("this|[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern TO_STRING_CALL =
      Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.toString\\(\\)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the entry
   */
  public KotlinStringTemplateCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ENTRY))) {
      final SyntaxNode entry = visit.node();
      final SyntaxNode expression = firstChild(entry, EXPRESSION);
      if (expression != null) {
        matchesEntry(visit, expression, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void matchesEntry(
      final SyntaxWalk.Visit visit, final SyntaxNode expression, final List<RuleMatch> matches) {
    final String text = SyntaxWalk.text(expression);
    final Matcher call = TO_STRING_CALL.matcher(text);
    if (call.matches()) {
      matches.add(CheckSupport.at(dotSpan(expression, call.group(1).length()), message));
    } else if (IDENTIFIER_OR_THIS.matcher(text).matches() && followedByLiteralBoundary(visit)) {
      matches.add(CheckSupport.at(expression.span(), message));
    }
  }

  private static boolean followedByLiteralBoundary(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> ancestors = visit.ancestors();
    final List<SyntaxNode> siblings = ancestors.get(ancestors.size() - 1).children();
    final int index = siblings.indexOf(visit.node());
    if (index < 0 || index + 1 >= siblings.size()) {
      return false;
    }
    final SyntaxNode next = siblings.get(index + 1);
    return CLOSING_QUOTE.equals(next.kind())
        || CONTENT.equals(next.kind()) && startsWithPlainCharacter(next);
  }

  private static boolean startsWithPlainCharacter(final SyntaxNode content) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(content);
    if (leaves.isEmpty() || !TEXT.equals(leaves.get(0).kind())) {
      return false;
    }
    final char first = SyntaxWalk.text(leaves.get(0)).charAt(0);
    return first != '_' && !Character.isLetterOrDigit(first);
  }

  private static Span dotSpan(final SyntaxNode expression, final int nameLength) {
    final Span start = expression.span();
    final int column = start.startColumn() + nameLength;
    final int offset = start.startOffset() + nameLength;
    return new Span(start.startLine(), column, start.startLine(), column + 1, offset, offset + 1);
  }

  private static SyntaxNode firstChild(final SyntaxNode node, final String kind) {
    return node.children().stream()
        .filter(child -> kind.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }
}
