package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a numeric literal whose digits are not the digits of the number it is read as, like
 * ESLint {@code no-loss-of-precision}. A base-ten literal is compared with its value rounded to the
 * same number of significant digits; a binary, octal or hexadecimal literal is compared with the
 * digits of its value in that base.
 */
public final class NodeLossOfPrecisionCheck implements RuleCheck {

  private static final Set<String> NUMBERS =
      Set.of(
          "DecimalLiteral",
          "HexIntegerLiteral",
          "OctalIntegerLiteral",
          "OctalIntegerLiteral2",
          "BinaryIntegerLiteral");
  private static final String PREFIX_LETTERS = "xXbBoO";
  private static final String OCTAL_LEGACY = "0[0-7]+";
  private static final String SEPARATOR = "_";
  private static final int MAX_PRECISION = 100;
  private static final int PREFIX_LENGTH = 2;
  private static final int BINARY = 2;
  private static final int OCTAL = 8;
  private static final int HEX = 16;

  /** A number as its significant digits and the order of magnitude of the first digit. */
  private record Scientific(String coefficient, int magnitude) {}

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeLossOfPrecisionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode leaf : LeafText.leaves(root)) {
      if (NUMBERS.contains(leaf.kind()) && loses(LeafText.of(leaf))) {
        matches.add(new RuleMatch(leaf.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean loses(final String raw) {
    final String digits = raw.replace(SEPARATOR, "");
    return isBaseTen(raw) ? baseTenLoses(digits) : otherBaseLoses(digits);
  }

  private static boolean isBaseTen(final String raw) {
    final boolean prefixed =
        raw.length() > 1 && raw.charAt(0) == '0' && PREFIX_LETTERS.indexOf(raw.charAt(1)) >= 0;
    return !prefixed && !raw.matches(OCTAL_LEGACY);
  }

  private static boolean baseTenLoses(final String raw) {
    final double value = Double.parseDouble(raw);
    if (Double.compare(value, 0.0) == 0) {
      return false;
    }
    if (Double.isInfinite(value)) {
      return true;
    }
    final Scientific requested = requested(raw.toLowerCase(Locale.ROOT));
    final int precision = requested.coefficient().length();
    return precision > MAX_PRECISION || !requested.equals(stored(value, precision));
  }

  private static Scientific requested(final String lower) {
    final int exponentAt = lower.indexOf('e');
    final String mantissa = exponentAt < 0 ? lower : lower.substring(0, exponentAt);
    final Scientific base =
        mantissa.indexOf('.') >= 0 ? normalizeFloat(mantissa) : normalizeInteger(mantissa);
    if (exponentAt < 0) {
      return base;
    }
    return new Scientific(
        base.coefficient(), base.magnitude() + Integer.parseInt(lower.substring(exponentAt + 1)));
  }

  private static Scientific normalizeInteger(final String text) {
    final String trimmed = removeLeadingZeros(text);
    return new Scientific(removeTrailingZeros(trimmed), trimmed.length() - 1);
  }

  private static Scientific normalizeFloat(final String text) {
    final String trimmed = removeLeadingZeros(text);
    final int point = trimmed.indexOf('.');
    if (point == 0) {
      final String significant = removeLeadingZeros(trimmed.substring(1));
      return new Scientific(significant, significant.length() - trimmed.length());
    }
    if (point < 0) {
      return new Scientific(trimmed, trimmed.length() - 1);
    }
    return new Scientific(trimmed.replace(".", ""), point - 1);
  }

  /** The value rounded to the given significant digits, as JavaScript {@code toPrecision} does. */
  private static Scientific stored(final double value, final int precision) {
    final BigDecimal rounded =
        new BigDecimal(value).round(new MathContext(precision, RoundingMode.HALF_UP));
    final String digits = rounded.unscaledValue().toString();
    final int magnitude = digits.length() - 1 - rounded.scale();
    return new Scientific(digits + "0".repeat(precision - digits.length()), magnitude);
  }

  private static boolean otherBaseLoses(final String raw) {
    final String upper = raw.toUpperCase(Locale.ROOT);
    final int radix = radixOf(upper);
    final String digits = isPrefixed(raw) ? raw.substring(PREFIX_LENGTH) : raw;
    final double value = new BigInteger(digits, radix).doubleValue();
    if (Double.isInfinite(value)) {
      return true;
    }
    final String written = new BigDecimal(value).toBigInteger().toString(radix);
    return !upper.endsWith(written.toUpperCase(Locale.ROOT));
  }

  private static boolean isPrefixed(final String raw) {
    return raw.length() > 1 && raw.charAt(0) == '0' && PREFIX_LETTERS.indexOf(raw.charAt(1)) >= 0;
  }

  private static int radixOf(final String upper) {
    if (upper.startsWith("0B")) {
      return BINARY;
    }
    return upper.startsWith("0X") ? HEX : OCTAL;
  }

  private static String removeLeadingZeros(final String text) {
    for (int index = 0; index < text.length(); index++) {
      if (text.charAt(index) != '0') {
        return text.substring(index);
      }
    }
    return text;
  }

  private static String removeTrailingZeros(final String text) {
    for (int index = text.length() - 1; index >= 0; index--) {
      if (text.charAt(index) != '0') {
        return text.substring(0, index + 1);
      }
    }
    return text;
  }
}
