package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a Kotlin range of two integer literals that is always empty: {@code 10..1}, {@code 5
 * until 1} or {@code 1 downTo 5}. The text of a node has no whitespace, since it joins the tokens.
 */
public final class InvalidRangeCheck implements UnitCheck {

  private static final String RANGE = "rangeExpression";
  private static final String INFIX = "infixFunctionCall";
  private static final String DOWN_TO = "downTo";
  private static final Pattern LITERALS =
      Pattern.compile("^(-?\\d+)L?(\\.\\.|until|downTo)(-?\\d+)L?$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the range
   */
  public InvalidRangeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(RANGE, INFIX))) {
      final SyntaxNode node = visit.node();
      final Matcher literals = LITERALS.matcher(SyntaxWalk.text(node));
      if (literals.matches() && empty(node.kind(), literals)) {
        matches.add(CheckSupport.at(node.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  /** A {@code ..} is a range node and the named operators are infix calls; no node is both. */
  private static boolean empty(final String kind, final Matcher literals) {
    final String operator = literals.group(2);
    final boolean ownKind = RANGE.equals(kind) == "..".equals(operator);
    final int order =
        new BigInteger(literals.group(1)).compareTo(new BigInteger(literals.group(3)));
    return ownKind && (DOWN_TO.equals(operator) ? order < 0 : order > 0);
  }
}
