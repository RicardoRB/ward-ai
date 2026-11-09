package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a clamp whose result is constant: {@code Math.min(a, Math.max(b, x))} with a below b, or
 * {@code Math.max(a, Math.min(b, x))} with a above b. Only the first argument of the inner call
 * must be a number literal; the rule compares the two literals.
 */
public final class BiomeConstantClampCheck implements RuleCheck {

  private static final Pattern CLAMP =
      Pattern.compile(
          "^Math\\.(min|max)\\((-?\\d+(?:\\.\\d+)?),Math\\.(min|max)\\((-?\\d+(?:\\.\\d+)?),");
  private static final String CALL = "singleExpression";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeConstantClampCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(CALL)).stream()
        .filter(visit -> constantClamp(SyntaxWalk.text(visit.node())))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean constantClamp(final String text) {
    final Matcher clamp = CLAMP.matcher(text);
    if (!clamp.find()) {
      return false;
    }
    final String outer = clamp.group(1);
    final double outerValue = Double.parseDouble(clamp.group(2));
    final String inner = clamp.group(3);
    final double innerValue = Double.parseDouble(clamp.group(4));
    return "min".equals(outer) && "max".equals(inner) && outerValue < innerValue
        || "max".equals(outer) && "min".equals(inner) && outerValue > innerValue;
  }
}
