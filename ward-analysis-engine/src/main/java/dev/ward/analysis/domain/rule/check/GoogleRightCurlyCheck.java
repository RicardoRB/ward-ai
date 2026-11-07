package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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
  private static final String CLOSE = "'}'";
  private static final String STATEMENT = "statement";
  private static final String IF = "'if'";
  private static final String TRY = "'try'";
  private static final String BLOCK = "block";
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
    final Set<SyntaxNode> closers = closingBraces(root);
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

  /** The closing braces of the if, else, try, catch and finally blocks, by identity. */
  private static Set<SyntaxNode> closingBraces(final SyntaxNode root) {
    final Set<SyntaxNode> closers = Collections.newSetFromMap(new IdentityHashMap<>());
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of(STATEMENT, "catchClause", "finallyBlock"))) {
      final SyntaxNode node = visit.node();
      if (!STATEMENT.equals(node.kind()) || startsWithIfOrTry(node)) {
        node.children().forEach(child -> addClosingBrace(child, closers));
      }
    }
    return closers;
  }

  private static boolean startsWithIfOrTry(final SyntaxNode statement) {
    return !statement.children().isEmpty()
        && (IF.equals(statement.children().get(0).kind())
            || TRY.equals(statement.children().get(0).kind()));
  }

  private static void addClosingBrace(final SyntaxNode child, final Set<SyntaxNode> closers) {
    final SyntaxNode block =
        STATEMENT.equals(child.kind()) && !child.children().isEmpty()
            ? child.children().get(0)
            : child;
    if (BLOCK.equals(block.kind()) && !block.children().isEmpty()) {
      final SyntaxNode last = block.children().get(block.children().size() - 1);
      if (CLOSE.equals(last.kind())) {
        closers.add(last);
      }
    }
  }
}
