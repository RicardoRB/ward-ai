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
 * Reports an equality test {@code (x & mask) == value} whose value has a bit that the mask clears,
 * so the test can never be true. Only literal masks and values are compared.
 */
public final class BitMaskComparisonCheck implements UnitCheck {

  private static final String EXPRESSION = "expression";
  private static final int MASK = 1;
  private static final int VALUE = 3;
  private static final int HEX_PREFIX = 2;
  private static final int RADIX = 16;
  private static final Pattern COMPARISON =
      Pattern.compile(
          "^\\(\\s*[^()]*&\\s*(0[xX][0-9a-fA-F]+|\\d+)[lL]?\\s*\\)\\s*(==|!=)\\s*"
              + "(0[xX][0-9a-fA-F]+|\\d+)[lL]?$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the comparison
   */
  public BitMaskComparisonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final String text = SyntaxWalk.text(visit.node()).strip();
      final Matcher matcher = COMPARISON.matcher(text);
      if (matcher.matches() && clearsRequiredBit(matcher)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean clearsRequiredBit(final Matcher matcher) {
    final BigInteger mask = literal(matcher.group(MASK));
    final BigInteger value = literal(matcher.group(VALUE));
    return value.andNot(mask).signum() != 0;
  }

  private static BigInteger literal(final String text) {
    final String digits =
        text.endsWith("l") || text.endsWith("L") ? text.substring(0, text.length() - 1) : text;
    if (digits.startsWith("0x") || digits.startsWith("0X")) {
      return new BigInteger(digits.substring(HEX_PREFIX), RADIX);
    }
    return new BigInteger(digits);
  }
}
