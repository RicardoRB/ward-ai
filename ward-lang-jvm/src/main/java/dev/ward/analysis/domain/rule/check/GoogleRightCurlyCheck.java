package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the right curly braces of if, else, try, catch and finally blocks that break the Google
 * Java Style layout: a non-empty block ends with a brace on its own line, and a brace that is
 * followed by {@code else}, {@code catch}, {@code finally} or a comma has no line break after it.
 */
public final class GoogleRightCurlyCheck implements RuleCheck {

  private static final String OPEN = "'{'";
  private static final Set<String> CONTINUATIONS = Set.of("'else'", "'catch'", "'finally'", "','");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public GoogleRightCurlyCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<SyntaxNode> closers = IfTryBraces.closingBraces(root);
    final List<RuleMatch> matches = new ArrayList<>();
    final TokenSequence tokens = TokenSequence.of(root);
    for (final TokenSequence.Token token : tokens.all()) {
      if (closers.contains(token.node()) && isBadLayout(tokens, token)) {
        matches.add(CheckSupport.at(token.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isBadLayout(final TokenSequence tokens, final TokenSequence.Token token) {
    final SyntaxNode close = token.node();
    final SyntaxNode before = tokens.before(token);
    final SyntaxNode after = tokens.after(token);
    final boolean empty = before != null && OPEN.equals(before.kind());
    final boolean sharesLineBefore =
        before != null && before.span().endLine() == close.span().startLine();
    final boolean breaksAfter =
        after != null
            && CONTINUATIONS.contains(after.kind())
            && after.span().startLine() != close.span().endLine();
    return !empty && sharesLineBefore || breaksAfter;
  }
}
