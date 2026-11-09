package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an {@code apply} call that is a whole statement, with a body of one member access, as
 * detekt's {@code UnnecessaryApply} does. An {@code apply} whose result is used, such as the
 * builder idiom in an assignment or a return, is left alone.
 */
public final class KotlinUnnecessaryApplyCheck implements RuleCheck {

  private static final String CALL = "postfixUnaryExpression";
  private static final String STATEMENT = "statement";
  private static final String RETURN = "return";
  private static final String THROW = "throw";
  private static final Pattern BARE_APPLY =
      Pattern.compile("\\.apply\\{(?:this\\.)?\\w+(?:=[^={}]+)?\\}$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public KotlinUnnecessaryApplyCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CALL))) {
      final String text = SyntaxWalk.text(visit.node());
      if (!jumpPrefixed(text) && BARE_APPLY.matcher(text).find() && wholeStatement(visit, text)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** A return or throw is parsed into the call it returns, so its text starts with the keyword. */
  private static boolean jumpPrefixed(final String text) {
    return text.startsWith(RETURN) || text.startsWith(THROW);
  }

  private static boolean wholeStatement(final SyntaxWalk.Visit visit, final String text) {
    final List<SyntaxNode> ancestors = visit.ancestors();
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (STATEMENT.equals(ancestors.get(index).kind())) {
        return text.equals(SyntaxWalk.text(ancestors.get(index)));
      }
    }
    return false;
  }
}
