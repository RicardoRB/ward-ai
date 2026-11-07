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
 * Reports a clamp {@code Math.max(Math.min(x, a), b)} with {@code a < b}, or {@code
 * Math.min(Math.max(x, a), b)} with {@code a > b}: the constant bound always wins, so the call
 * returns a constant.
 */
public final class ConstantClampCheck implements UnitCheck {

  private static final String EXPRESSION = "expression";
  private static final int FIRST = 1;
  private static final int SECOND = 2;
  private static final Pattern MAX_OF_MIN =
      Pattern.compile(
          "^Math\\.max\\(\\s*Math\\.min\\(\\s*[^,()]+,\\s*(-?\\d+)\\s*\\)\\s*,\\s*(-?\\d+)\\s*\\)$");
  private static final Pattern MIN_OF_MAX =
      Pattern.compile(
          "^Math\\.min\\(\\s*Math\\.max\\(\\s*[^,()]+,\\s*(-?\\d+)\\s*\\)\\s*,\\s*(-?\\d+)\\s*\\)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public ConstantClampCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final String text = SyntaxWalk.text(visit.node()).strip();
      if (invalid(text)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean invalid(final String text) {
    final Matcher maxOfMin = MAX_OF_MIN.matcher(text);
    if (maxOfMin.matches()) {
      return bound(maxOfMin, SECOND).compareTo(bound(maxOfMin, FIRST)) > 0;
    }
    final Matcher minOfMax = MIN_OF_MAX.matcher(text);
    return minOfMax.matches() && bound(minOfMax, FIRST).compareTo(bound(minOfMax, SECOND)) > 0;
  }

  private static BigInteger bound(final Matcher matcher, final int group) {
    return new BigInteger(matcher.group(group));
  }
}
