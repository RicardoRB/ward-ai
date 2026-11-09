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
  private static final int ONE = 1;
  private static final int FIVE = 5;
  private static final char ZERO_DIGIT = '0';
  private static final double ZERO = 0.0;
  private static final int MANTISSA_BITS = 52;
  private static final int EXPONENT_BIAS = 1075;
  private static final int SUBNORMAL_EXPONENT = -1074;
  private static final long HIDDEN_BIT = 1L << MANTISSA_BITS;
  private static final long MANTISSA_MASK = HIDDEN_BIT - 1;
  private static final int EXPONENT_MASK = 0x7ff;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeLossOfPrecisionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** A number as its significant digits and the order of magnitude of the first digit. */
  private record Scientific(String coefficient, int magnitude) {

    /** The normalization of an integer, whose trailing zeros are not significant. */
    static Scientific ofInteger(final String text) {
      final String trimmed = removeLeadingZeros(text);
      return new Scientific(removeTrailingZeros(trimmed), trimmed.length() - ONE);
    }

    /** The normalization of a decimal, whose zeros are kept as written. */
    static Scientific ofFloat(final String text) {
      final String trimmed = removeLeadingZeros(text);
      final int point = trimmed.indexOf('.');
      if (point == 0) {
        final String significant = removeLeadingZeros(trimmed.substring(ONE));
        return new Scientific(significant, significant.length() - trimmed.length());
      }
      if (point < 0) {
        return new Scientific(trimmed, trimmed.length() - ONE);
      }
      return new Scientific(trimmed.replace(".", ""), point - ONE);
    }

    private static String removeLeadingZeros(final String text) {
      for (int index = 0; index < text.length(); index++) {
        if (text.charAt(index) != ZERO_DIGIT) {
          return text.substring(index);
        }
      }
      return text;
    }

    private static String removeTrailingZeros(final String text) {
      for (int index = text.length() - ONE; index >= 0; index--) {
        if (text.charAt(index) != ZERO_DIGIT) {
          return text.substring(0, index + ONE);
        }
      }
      return text;
    }
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
    return !isPrefixed(raw) && !raw.matches(OCTAL_LEGACY);
  }

  private static boolean isPrefixed(final String raw) {
    return raw.length() > ONE
        && raw.charAt(0) == '0'
        && PREFIX_LETTERS.indexOf(raw.charAt(ONE)) >= 0;
  }

  private static boolean baseTenLoses(final String raw) {
    final double value = Double.parseDouble(raw);
    if (Double.compare(value, ZERO) == 0) {
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
        mantissa.indexOf('.') >= 0 ? Scientific.ofFloat(mantissa) : Scientific.ofInteger(mantissa);
    if (exponentAt < 0) {
      return base;
    }
    return new Scientific(
        base.coefficient(), base.magnitude() + Integer.parseInt(lower.substring(exponentAt + ONE)));
  }

  /** The value rounded to the given significant digits, as JavaScript {@code toPrecision} does. */
  private static Scientific stored(final double value, final int precision) {
    final BigDecimal rounded = exact(value).round(new MathContext(precision, RoundingMode.HALF_UP));
    final String digits = rounded.unscaledValue().toString();
    final int magnitude = digits.length() - ONE - rounded.scale();
    return new Scientific(digits + "0".repeat(precision - digits.length()), magnitude);
  }

  /** The exact decimal value of a finite double, which a double constructor would also give. */
  private static BigDecimal exact(final double value) {
    final long bits = Double.doubleToRawLongBits(value);
    final int biased = (int) (bits >>> MANTISSA_BITS) & EXPONENT_MASK;
    final long fraction = bits & MANTISSA_MASK;
    final long significand = biased == 0 ? fraction : fraction | HIDDEN_BIT;
    final int exponent = biased == 0 ? SUBNORMAL_EXPONENT : biased - EXPONENT_BIAS;
    if (exponent >= 0) {
      return new BigDecimal(BigInteger.valueOf(significand).shiftLeft(exponent));
    }
    final int places = -exponent;
    return new BigDecimal(
        BigInteger.valueOf(significand).multiply(BigInteger.valueOf(FIVE).pow(places)), places);
  }

  private static boolean otherBaseLoses(final String raw) {
    final String upper = raw.toUpperCase(Locale.ROOT);
    final int radix = upper.startsWith("0B") ? BINARY : upper.startsWith("0X") ? HEX : OCTAL;
    final String digits = isPrefixed(raw) ? raw.substring(PREFIX_LENGTH) : raw;
    final double value = new BigInteger(digits, radix).doubleValue();
    if (Double.isInfinite(value)) {
      return true;
    }
    final String written = exact(value).toBigInteger().toString(radix);
    return !upper.endsWith(written.toUpperCase(Locale.ROOT));
  }
}
