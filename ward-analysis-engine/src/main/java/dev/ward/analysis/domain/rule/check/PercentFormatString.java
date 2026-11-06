package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The placeholders of a printf-style format string, as Python's {@code %} operator reads them: the
 * named and positional conversions, the {@code *} widths that take an argument, an incomplete
 * conversion at the end, and the first character the conversion table does not support.
 */
final class PercentFormatString {

  private static final String CONVERSIONS = "diouxXeEfFgGcrsa";
  private static final String FLAGS = "#0- +";
  private static final String LENGTHS = "hlL";
  private static final String NUMERIC = "diouxXeEfFgG";

  private final List<String> named = new ArrayList<>();
  private int positional;
  private boolean star;
  private boolean incomplete;
  private boolean numeric;
  private Optional<Character> unsupported = Optional.empty();

  private PercentFormatString() {}

  /** Reads every placeholder of {@code text}, the contents of the string literal. */
  static PercentFormatString parse(final String text) {
    final PercentFormatString parsed = new PercentFormatString();
    int index = 0;
    while (index < text.length() && !parsed.incomplete) {
      index = text.charAt(index) == '%' ? parsed.placeholder(text, index + 1) : index + 1;
    }
    return parsed;
  }

  /** The names of the named conversions, in order. */
  List<String> named() {
    return List.copyOf(named);
  }

  /** The number of positional conversions, a {@code *} width or precision counting as one. */
  int positional() {
    return positional;
  }

  /** Whether a {@code *} width or precision takes an argument. */
  boolean star() {
    return star;
  }

  /** Whether a conversion takes a number. */
  boolean numeric() {
    return numeric;
  }

  /** Whether the string ends inside a conversion. */
  boolean incomplete() {
    return incomplete;
  }

  /** The first character that is not a supported conversion, if any. */
  Optional<Character> unsupported() {
    return unsupported;
  }

  private int placeholder(final String text, final int start) {
    if (start < text.length() && text.charAt(start) == '%') {
      return start + 1;
    }
    int index = start;
    if (index < text.length() && text.charAt(index) == '(') {
      final int close = text.indexOf(')', index);
      if (close < 0) {
        incomplete = true;
        return text.length();
      }
      named.add(text.substring(index + 1, close));
      index = close + 1;
    }
    index = skipFlags(text, index);
    index = width(text, index);
    if (index < text.length() && text.charAt(index) == '.') {
      index = width(text, index + 1);
    }
    while (index < text.length() && LENGTHS.indexOf(text.charAt(index)) >= 0) {
      index++;
    }
    return conversion(text, index, start);
  }

  private int conversion(final String text, final int index, final int start) {
    if (index >= text.length()) {
      incomplete = true;
      return text.length();
    }
    final char conversion = text.charAt(index);
    if (CONVERSIONS.indexOf(conversion) < 0) {
      unsupported = unsupported.or(() -> Optional.of(conversion));
    }
    if (!isNamed(text, start)) {
      positional++;
    }
    numeric = numeric || NUMERIC.indexOf(conversion) >= 0;
    return index + 1;
  }

  private boolean isNamed(final String text, final int start) {
    return start < text.length() && text.charAt(start) == '(';
  }

  private int skipFlags(final String text, final int start) {
    int index = start;
    while (index < text.length() && FLAGS.indexOf(text.charAt(index)) >= 0) {
      index++;
    }
    return index;
  }

  private int width(final String text, final int start) {
    if (start < text.length() && text.charAt(start) == '*') {
      star = true;
      positional++;
      return start + 1;
    }
    int index = start;
    while (index < text.length() && Character.isDigit(text.charAt(index))) {
      index++;
    }
    return index;
  }
}
