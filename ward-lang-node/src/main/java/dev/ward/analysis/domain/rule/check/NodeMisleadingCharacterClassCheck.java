package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Reports a regular expression character class that matches differently than it looks, like ESLint
 * {@code no-misleading-character-class}: a surrogate pair without the {@code u} flag, a combining
 * mark after another character, a pair of regional indicators, or a modifier after an emoji. Each
 * is reported at the start of its regular expression literal, as ESLint does. The check reads the
 * literals; the {@code RegExp} constructor is not read.
 */
public final class NodeMisleadingCharacterClassCheck implements RuleCheck {

  private static final String REGEX = "RegularExpressionLiteral";
  private static final char SLASH = '/';
  private static final char BACKSLASH = '\\';
  private static final char OPEN_CLASS = '[';
  private static final char CLOSE_CLASS = ']';
  private static final int HEX_RADIX = 16;
  private static final int BRACED_START = 3;
  private static final int ESCAPE_WIDTH = 2;
  private static final int SHORT_HEX = 4;
  private static final int REGIONAL_FIRST = 0x1F1E6;
  private static final int REGIONAL_LAST = 0x1F1FF;
  private static final int EMOJI_FIRST = 0x1F000;
  private static final int MODIFIER_FIRST = 0x1F3FB;
  private static final int MODIFIER_LAST = 0x1F3FF;
  private static final int COMBINING_FIRST = 0x0300;
  private static final int COMBINING_LAST = 0x036F;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeMisleadingCharacterClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (REGEX.equals(node.kind())) {
      final OptionalInt at = literalMisleading(node);
      if (at.isPresent()) {
        matches.add(new RuleMatch(atLiteral(node, at.getAsInt() + 1), message));
      }
    }
    node.children().forEach(child -> collect(child, matches));
  }

  /** The index in the literal's pattern of a misleading sequence, read from its source. */
  private static OptionalInt literalMisleading(final SyntaxNode node) {
    final String literal = LeafText.of(node);
    final int end = literal.lastIndexOf(SLASH);
    return end > 0
        ? misleading(literal.substring(1, end), flagsOf(literal, end))
        : OptionalInt.empty();
  }

  /** The span of the character at an index of the literal, on the literal's line. */
  private static Span atLiteral(final SyntaxNode node, final int index) {
    final Span span = node.span();
    return new Span(
        span.startLine(),
        span.startColumn() + index,
        span.startLine(),
        span.startColumn() + index + 1,
        span.startOffset() + index,
        span.startOffset() + index + 1);
  }

  private static String flagsOf(final String literal, final int end) {
    return literal.substring(end + 1);
  }

  /** The index in the pattern of the first character of a misleading sequence, if there is one. */
  static OptionalInt misleading(final String pattern, final String flags) {
    final boolean unicode = flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0;
    OptionalInt at = OptionalInt.empty();
    int index = 0;
    while (at.isEmpty() && index < pattern.length()) {
      if (pattern.charAt(index) == OPEN_CLASS) {
        at = inClass(pattern, index, unicode);
        index = Classes.classEnd(pattern, index) + 1;
      } else {
        index += pattern.charAt(index) == BACKSLASH ? ESCAPE_WIDTH : 1;
      }
    }
    return at;
  }

  /** The index in the pattern of a misleading sequence inside the class that opens at an index. */
  private static OptionalInt inClass(final String pattern, final int open, final boolean unicode) {
    final int close = Classes.classEnd(pattern, open);
    final OptionalInt inner = Classes.classMisleading(pattern.substring(open + 1, close), unicode);
    return inner.isPresent() ? OptionalInt.of(open + 1 + inner.getAsInt()) : OptionalInt.empty();
  }

  /** The helpers that read a class body: its end, its characters and the sequences they form. */
  private static final class Classes {

    /** The index of the bracket that closes a class, or the end of the pattern. */
    static int classEnd(final String pattern, final int open) {
      int index = open + 1;
      while (index < pattern.length() && pattern.charAt(index) != CLOSE_CLASS) {
        index += pattern.charAt(index) == BACKSLASH ? 2 : 1;
      }
      return Math.min(index, pattern.length() - 1);
    }

    /**
     * The start of the first character of a misleading sequence in a class body, if there is one.
     */
    static OptionalInt classMisleading(final String body, final boolean unicode) {
      final List<Integer> points = new ArrayList<>();
      final List<Boolean> escapedPairs = new ArrayList<>();
      final List<Integer> starts = new ArrayList<>();
      int index = 0;
      while (index < body.length()) {
        final int[] read = readCodePoint(body, index);
        starts.add(index);
        points.add(read[0]);
        escapedPairs.add(read[2] == 1);
        index = read[1];
      }
      if (!unicode) {
        final int pair = surrogateEscapes(points, escapedPairs);
        if (pair >= 0) {
          return OptionalInt.of(starts.get(pair));
        }
      }
      for (int position = 1; position < points.size(); position++) {
        final boolean flagged =
            unicode && adjacentFlags(points.get(position - 1), points.get(position))
                || combining(points.get(position));
        if (flagged) {
          return OptionalInt.of(starts.get(position - 1));
        }
      }
      return OptionalInt.empty();
    }

    /** A high and a low surrogate, each written as an escape, next to each other in a class. */
    static int surrogateEscapes(final List<Integer> points, final List<Boolean> escaped) {
      for (int position = 1; position < points.size(); position++) {
        if (escaped.get(position - 1)
            && escaped.get(position)
            && Character.isHighSurrogate((char) points.get(position - 1).intValue())
            && Character.isLowSurrogate((char) points.get(position).intValue())) {
          return position - 1;
        }
      }
      return -1;
    }

    static boolean adjacentFlags(final int previous, final int current) {
      final boolean regional =
          inRange(previous, REGIONAL_FIRST, REGIONAL_LAST)
              && inRange(current, REGIONAL_FIRST, REGIONAL_LAST);
      final boolean modified =
          inRange(current, MODIFIER_FIRST, MODIFIER_LAST) && previous >= EMOJI_FIRST;
      return regional || modified;
    }

    static boolean combining(final int point) {
      return inRange(point, COMBINING_FIRST, COMBINING_LAST);
    }

    static boolean inRange(final int point, final int first, final int last) {
      return point >= first && point <= last;
    }

    /**
     * The code point at an index of a class body, the index after it, and whether it came from an
     * escape: {@code \\uXXXX}, {@code \\u{...}} or a single escaped character, else a plain
     * character.
     */
    static int[] readCodePoint(final String body, final int index) {
      if (body.charAt(index) != BACKSLASH || index + 1 >= body.length()) {
        final int point = body.codePointAt(index);
        return new int[] {point, index + Character.charCount(point), 0};
      }
      final char kind = body.charAt(index + 1);
      if (kind == 'u' && index + 2 < body.length() && body.charAt(index + 2) == '{') {
        final int close = body.indexOf('}', index + 3);
        return new int[] {
          Integer.parseInt(body.substring(index + BRACED_START, close), HEX_RADIX), close + 1, 1
        };
      }
      if (kind == 'u' && index + 2 + SHORT_HEX <= body.length()) {
        return new int[] {
          Integer.parseInt(body.substring(index + 2, index + 2 + SHORT_HEX), HEX_RADIX),
          index + 2 + SHORT_HEX,
          1
        };
      }
      return new int[] {kind, index + 2, 0};
    }
  }
}
