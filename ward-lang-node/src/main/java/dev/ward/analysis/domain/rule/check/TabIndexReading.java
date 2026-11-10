package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How the focusable rules read a tabIndex, as Biome 2.5.15's no-aria-hidden-on-focusable reads it.
 * A non-negative integer makes the element focusable; a negative integer, a non-numeric expression
 * or a missing value blocks the report; a value that does not parse (a decimal, a template, a
 * string with spaces) leaves the decision to the element's own interactivity.
 */
enum TabIndexReading {
  /** The tabIndex is zero or more: the element is focusable. */
  NON_NEGATIVE,
  /** The tabIndex blocks the report whatever the element is. */
  BLOCKED,
  /** The tabIndex does not read as a number: the element's interactivity decides. */
  UNREADABLE;

  private static final Pattern SIGNED_INTEGER = Pattern.compile("[+-]?\\d+");
  private static final Pattern INTEGER = Pattern.compile("\\d+");
  private static final Pattern DECIMAL = Pattern.compile("\\d+\\.\\d+");

  /** The reading of one tabIndex attribute, given the source its value is read from. */
  static TabIndexReading of(final SyntaxNode attribute, final String source) {
    final Optional<SyntaxNode> value = JsxAttributes.value(attribute);
    if (value.isEmpty()) {
      return BLOCKED;
    }
    final String raw = JsxTruthiness.raw(value.get(), source);
    if (raw.startsWith("{") && raw.endsWith("}")) {
      return expression(raw.substring(1, raw.length() - 1).trim());
    }
    return JsxTruthiness.isQuoted(raw) ? string(raw.substring(1, raw.length() - 1)) : UNREADABLE;
  }

  private static TabIndexReading string(final String text) {
    return SIGNED_INTEGER.matcher(text).matches() ? integer(text) : UNREADABLE;
  }

  private static TabIndexReading expression(final String inner) {
    if (INTEGER.matcher(inner).matches()) {
      return integer(inner);
    }
    return DECIMAL.matcher(inner).matches() ? UNREADABLE : BLOCKED;
  }

  /** An integer that fits an i32 reads by its sign; one that does not fit is unreadable. */
  private static TabIndexReading integer(final String text) {
    try {
      return Integer.parseInt(text) >= 0 ? NON_NEGATIVE : BLOCKED;
    } catch (final NumberFormatException exception) {
      return UNREADABLE;
    }
  }
}
