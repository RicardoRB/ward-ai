package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The placeholders of a printf-style format string, as Python's {@code %} operator reads them: the
 * named and positional conversions, whether a {@code *} width takes an argument, whether a
 * conversion takes a number, an incomplete conversion at the end, and the first character the
 * conversion table does not support.
 *
 * @param named the names of the named conversions, in order
 * @param positional the positional conversions, a {@code *} width counting as one
 * @param star whether a {@code *} width or precision takes an argument
 * @param incomplete whether the string ends inside a conversion
 * @param unsupported the first conversion character that is not supported, if any
 * @param numeric whether a conversion takes a number
 */
record PercentFormatString(
    List<String> named,
    int positional,
    boolean star,
    boolean incomplete,
    Optional<Character> unsupported,
    boolean numeric) {

  /** Copies the names. */
  PercentFormatString {
    named = List.copyOf(named);
  }

  /** Reads every placeholder of {@code text}, the contents of the string literal. */
  static PercentFormatString parse(final String text) {
    final Scanner scanner = new Scanner(text);
    scanner.scan();
    return scanner.result();
  }

  /** One pass over the text, collecting the conversions. */
  private static final class Scanner {

    private static final String CONVERSIONS = "diouxXeEfFgGcrsa";
    private static final String NUMERIC = "diouxXeEfFgG";
    private static final String FLAGS = "#0- +";
    private static final String LENGTHS = "hlL";
    private static final char PERCENT = '%';
    private static final char OPEN = '(';
    private static final char CLOSE = ')';
    private static final char STAR = '*';
    private static final char DOT = '.';

    private final String text;
    private final List<String> named = new ArrayList<>();
    private int index;
    private int positional;
    private boolean star;
    private boolean incomplete;
    private boolean numeric;
    private Optional<Character> unsupported = Optional.empty();

    Scanner(final String text) {
      this.text = text;
    }

    void scan() {
      while (index < text.length() && !incomplete) {
        if (text.charAt(index) == PERCENT) {
          index++;
          placeholder();
        } else {
          index++;
        }
      }
    }

    PercentFormatString result() {
      return new PercentFormatString(named, positional, star, incomplete, unsupported, numeric);
    }

    private void placeholder() {
      if (at(PERCENT)) {
        index++;
        return;
      }
      final boolean isNamed = at(OPEN);
      if (isNamed && !name()) {
        return;
      }
      flags();
      width();
      if (at(DOT)) {
        index++;
        width();
      }
      lengths();
      conversion(isNamed);
    }

    private boolean name() {
      final int close = text.indexOf(CLOSE, index);
      if (close < 0) {
        incomplete = true;
        return false;
      }
      named.add(text.substring(index + 1, close));
      index = close + 1;
      return true;
    }

    private void flags() {
      while (index < text.length() && FLAGS.indexOf(text.charAt(index)) >= 0) {
        index++;
      }
    }

    private void width() {
      if (at(STAR)) {
        star = true;
        positional++;
        index++;
        return;
      }
      while (index < text.length() && Character.isDigit(text.charAt(index))) {
        index++;
      }
    }

    private void lengths() {
      while (index < text.length() && LENGTHS.indexOf(text.charAt(index)) >= 0) {
        index++;
      }
    }

    private void conversion(final boolean isNamed) {
      if (index >= text.length()) {
        incomplete = true;
        return;
      }
      final char conversion = text.charAt(index);
      if (CONVERSIONS.indexOf(conversion) < 0) {
        unsupported = unsupported.or(() -> Optional.of(conversion));
      }
      if (!isNamed) {
        positional++;
      }
      numeric = numeric || NUMERIC.indexOf(conversion) >= 0;
      index++;
    }

    private boolean at(final char expected) {
      return index < text.length() && text.charAt(index) == expected;
    }
  }
}
