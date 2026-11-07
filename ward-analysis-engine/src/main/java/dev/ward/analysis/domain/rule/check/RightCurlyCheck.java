package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the right curly braces of if, else, try, catch and finally blocks. With {@code same}, a
 * brace followed by {@code else}, {@code catch} or {@code finally} shares its line with that word.
 * With {@code alone}, a brace shares its line with no other token except a closing {@code ;},
 * {@code )}, {@code ,} or {@code }}.
 */
public final class RightCurlyCheck implements RuleCheck {

  private static final String ALONE = "alone";
  private static final Set<String> CONTINUATIONS = Set.of("'else'", "'catch'", "'finally'");
  private static final Set<String> TRAILING = Set.of("';'", "')'", "','", "'}'");

  private final boolean alone;
  private final String message;

  /**
   * Creates the check.
   *
   * @param option {@code same} or {@code alone}
   * @param message the message
   */
  public RightCurlyCheck(final String option, final String message) {
    if (!ALONE.equals(option) && !"same".equals(option)) {
      throw new IllegalArgumentException("option must be same or alone, not " + option);
    }
    this.alone = ALONE.equals(option);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<SyntaxNode> closers = IfTryBraces.closingBraces(root);
    final List<RuleMatch> matches = new ArrayList<>();
    final TokenSequence tokens = TokenSequence.of(root);
    for (final TokenSequence.Token token : tokens.all()) {
      if (closers.contains(token.node()) && isBad(tokens, token)) {
        matches.add(CheckSupport.at(token.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isBad(final TokenSequence tokens, final TokenSequence.Token token) {
    final SyntaxNode close = token.node();
    final SyntaxNode after = tokens.after(token);
    if (after == null) {
      return false;
    }
    final boolean sameLine = after.span().startLine() == close.span().endLine();
    if (alone) {
      return sameLine && !TRAILING.contains(after.kind());
    }
    return CONTINUATIONS.contains(after.kind()) && !sameLine;
  }
}
