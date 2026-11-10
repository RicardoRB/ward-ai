package dev.ward.analysis.domain.rule.check;

import java.util.Locale;
import java.util.regex.Pattern;

/** The type a returned literal has, judged from its text. */
final class ReturnLiteral {
  private static final Pattern INT_LITERAL =
      Pattern.compile("[+-]?(0[xX][0-9a-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|\\d[\\d_]*)");
  private static final Pattern FLOAT_LITERAL =
      Pattern.compile(
          "[+-]?(\\d[\\d_]*\\.[\\d_]*|\\.\\d[\\d_]*)([eE][+-]?\\d+)?|[+-]?\\d[\\d_]*[eE][+-]?\\d+");

  private ReturnLiteral() {}

  static DunderReturnCheck.Literal classify(final String value) {
    if (value.isEmpty() || "None".equals(value)) {
      return DunderReturnCheck.Literal.NONE;
    }
    if ("True".equals(value) || "False".equals(value)) {
      return DunderReturnCheck.Literal.BOOL;
    }
    if (INT_LITERAL.matcher(value).matches()) {
      return DunderReturnCheck.Literal.INT;
    }
    if (FLOAT_LITERAL.matcher(value).matches()) {
      return DunderReturnCheck.Literal.FLOAT;
    }
    return classifyQuoted(value);
  }

  static DunderReturnCheck.Literal classifyQuoted(final String value) {
    final int quote = firstQuote(value);
    if (quote < 0) {
      return classifyBracketed(value);
    }
    final String prefix = value.substring(0, quote).toLowerCase(Locale.ROOT);
    if (!prefix.matches("[rbuf]*")) {
      return DunderReturnCheck.Literal.UNKNOWN;
    }
    return prefix.contains("b") ? DunderReturnCheck.Literal.BYTES : DunderReturnCheck.Literal.STR;
  }

  static int firstQuote(final String value) {
    final int single = value.indexOf('\'');
    final int doubleQuote = value.indexOf('"');
    return single < 0 ? doubleQuote : doubleQuote < 0 ? single : Math.min(single, doubleQuote);
  }

  static DunderReturnCheck.Literal classifyBracketed(final String value) {
    return switch (value.charAt(0)) {
      case '[' -> DunderReturnCheck.Literal.LIST;
      case '{' ->
          value.contains(":") || "{}".equals(value)
              ? DunderReturnCheck.Literal.DICT
              : DunderReturnCheck.Literal.SET;
      case '(' -> DunderReturnCheck.Literal.TUPLE;
      default -> DunderReturnCheck.Literal.UNKNOWN;
    };
  }
}
