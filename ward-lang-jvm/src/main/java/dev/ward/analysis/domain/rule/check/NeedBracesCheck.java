package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the bodies of {@code if}, {@code else}, {@code for}, {@code while} and {@code do} that
 * are not blocks. An {@code else} that starts an {@code if} is not reported, because that inner
 * {@code if} is checked itself. An empty loop body is reported unless {@code allowEmptyLoopBody}.
 */
public final class NeedBracesCheck implements RuleCheck {

  private static final String STATEMENT = "statement";
  private static final String BLOCK = "block";
  private static final String IF = "'if'";
  private static final String ELSE = "'else'";
  private static final String SEMICOLON = "';'";
  private static final Set<String> KEYWORDS = Set.of(IF, ELSE, "'for'", "'while'", "'do'");

  private final boolean allowEmptyLoopBody;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowEmptyLoopBody whether a loop may have an empty {@code ;} body
   * @param message the message
   */
  public NeedBracesCheck(final boolean allowEmptyLoopBody, final String message) {
    this.allowEmptyLoopBody = allowEmptyLoopBody;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENT))) {
      matchesOf(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void matchesOf(final SyntaxNode statement, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = statement.children();
    if (children.isEmpty() || !KEYWORDS.contains(children.get(0).kind())) {
      return;
    }
    SyntaxNode keyword = children.get(0);
    for (int index = 1; index < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (ELSE.equals(child.kind())) {
        keyword = child;
      } else if (STATEMENT.equals(child.kind()) && needsBraces(keyword.kind(), child)) {
        matches.add(CheckSupport.at(keyword.span(), message));
      }
    }
  }

  private boolean needsBraces(final String keyword, final SyntaxNode body) {
    if (isBlock(body) || ELSE.equals(keyword) && startsIf(body)) {
      return false;
    }
    return !(allowEmptyLoopBody
        && isEmptyBody(body)
        && !IF.equals(keyword)
        && !ELSE.equals(keyword));
  }

  private static boolean isBlock(final SyntaxNode body) {
    return !body.children().isEmpty() && BLOCK.equals(body.children().get(0).kind());
  }

  private static boolean startsIf(final SyntaxNode body) {
    return !body.children().isEmpty() && IF.equals(body.children().get(0).kind());
  }

  private static boolean isEmptyBody(final SyntaxNode body) {
    return body.children().size() == 1 && SEMICOLON.equals(body.children().get(0).kind());
  }
}
