package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;

/**
 * Reads the names inside the braces of a format string: {@code {name}}, {@code {name:spec}} and the
 * {@code name$} width and precision arguments of a spec. {@code {{} is text.
 */
final class RustFormatNames {
  private static final char OPEN = '{';
  private static final char CLOSE = '}';
  private static final char LINE_BREAK = '\n';
  private static final char ARGUMENT = '$';
  private static final char SPEC = ':';
  private static final char UNDERSCORE = '_';
  private static final int DOUBLED = 2;

  private RustFormatNames() {}

  /** Records a read for every name the string literal {@code literal} holds. */
  static void read(final ScopeBuilder builder, final SyntaxNode literal) {
    final String text = literal.fields().get("text");
    int index = 0;
    while (index < text.length()) {
      if (text.charAt(index) != OPEN) {
        index++;
      } else if (index + 1 < text.length() && text.charAt(index + 1) == OPEN) {
        index += DOUBLED;
      } else {
        index = field(builder, literal, text, index + 1);
      }
    }
  }

  private static int field(
      final ScopeBuilder builder,
      final SyntaxNode literal,
      final String text,
      final int start
  ) {
    final int end = identifierEnd(text, start);
    if (end > start) {
      record(builder, literal, text, start, end);
    }
    final int close = text.indexOf(CLOSE, end);
    if (close > end && text.charAt(end) == SPEC) {
      specNames(builder, literal, text, end, close);
    }
    return Math.max(end, start);
  }

  private static void specNames(
      final ScopeBuilder builder,
      final SyntaxNode literal,
      final String text,
      final int from,
      final int close
  ) {
    int index = from;
    while (index < close) {
      final int end = identifierEnd(text, index);
      if (end > index
          && end < close
          && text.charAt(end) == ARGUMENT
          && !isIdentifierPart(text.charAt(index - 1), false)) {
        record(builder, literal, text, index, end);
      }
      index = Math.max(end, index + 1);
    }
  }

  private static void record(
      final ScopeBuilder builder,
      final SyntaxNode literal,
      final String text,
      final int from,
      final int to
  ) {
    builder.reference(
        text.substring(from, to),
        inside(literal.span(), text, from, to),
        ReferenceAccess.READ
    );
  }

  private static int identifierEnd(final String text, final int start) {
    int end = start;
    while (end < text.length() && isIdentifierPart(text.charAt(end), end == start)) {
      end++;
    }
    return end;
  }

  private static boolean isIdentifierPart(final char c, final boolean first) {
    return c == UNDERSCORE || (first ? Character.isLetter(c) : Character.isLetterOrDigit(c));
  }

  /** The region of {@code [from, to)} of a literal's text, which may follow line breaks. */
  private static Span inside(final Span literal, final String text, final int from, final int to) {
    final int lines = (int)text.substring(0, from).chars().filter(c -> c == LINE_BREAK).count();
    final int lineStart = text.lastIndexOf(LINE_BREAK, from - 1);
    final int column = lines == 0 ? literal.startColumn() + from : from - lineStart;
    final int line = literal.startLine() + lines;
    return new Span(
        line,
        column,
        line,
        column + (to - from),
        literal.startOffset() + from,
        literal.startOffset() + to
    );
  }
}
