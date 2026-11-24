package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** Reads a run of tokens of one expression: its parts at depth zero, its calls and its spans. */
final class PythonTokens {
  private static final String NAME = "[A-Za-z_]\\w*";
  private static final Set<String> OPEN = Set.of("(", "[", "{");
  private static final Set<String> CLOSE = Set.of(")", "]", "}");

  private PythonTokens() {}

  /** A half-open range of token positions. */
  record Range(int from, int to) {}

  /**
   * A call {@code callee(arguments)} that fills a range, with the range of each argument and the
   * range of the callee, which is the span of the call site.
   */
  record Call(String callee, List<Range> arguments, Range whole, Range name) {}

  /** The texts of the tokens. */
  static List<String> texts(final List<SyntaxNode> tokens) {
    return tokens.stream().map(PythonSyntax::text).toList();
  }

  /** The span from the first token of the range to its last. */
  static Span span(final List<SyntaxNode> tokens, final Range range) {
    final Span start = tokens.get(range.from()).span();
    final Span end = tokens.get(range.to() - 1).span();
    return new Span(
        start.startLine(),
        start.startColumn(),
        end.endLine(),
        end.endColumn(),
        start.startOffset(),
        end.endOffset()
    );
  }

  /** The parts of the range between the depth-zero tokens equal to {@code word}. */
  static List<Range> split(final List<String> tokens, final Range range, final String word) {
    final List<Integer> cuts = new ArrayList<>();
    int depth = 0;
    for (int index = range.from(); index < range.to(); index++) {
      final String token = tokens.get(index);
      depth += OPEN.contains(token) ? 1 : CLOSE.contains(token) ? -1 : 0;
      if (depth == 0 && word.equals(token)) {
        cuts.add(index);
      }
    }
    return parts(range, cuts);
  }

  private static List<Range> parts(final Range range, final List<Integer> cuts) {
    final List<Integer> starts = new ArrayList<>(List.of(range.from()));
    cuts.forEach(cut -> starts.add(cut + 1));
    final List<Integer> ends = new ArrayList<>(cuts);
    ends.add(range.to());
    return IntStream.range(0, starts.size()).mapToObj(
        i -> new Range(starts.get(i), ends.get(i))
    ).toList();
  }

  /** Whether a depth-zero token of the range is {@code word}. */
  static boolean has(final List<String> tokens, final Range range, final String word) {
    return split(tokens, range, word).size() > 1;
  }

  /** The range without the pairs of parentheses that enclose all of it. */
  static Range unwrapped(final List<String> tokens, final Range range) {
    final boolean wrapped =
        range.to() - range.from() > 2
            && "(".equals(tokens.get(range.from()))
            && closeOf(tokens, range.from()) == range.to() - 1;
    return wrapped ? unwrapped(tokens, new Range(range.from() + 1, range.to() - 1)) : range;
  }

  /** The position of the bracket that closes the one at {@code open}, or -1. */
  static int closeOf(final List<String> tokens, final int open) {
    int depth = 0;
    for (int index = open; index < tokens.size(); index++) {
      depth += OPEN.contains(tokens.get(index)) ? 1 : CLOSE.contains(tokens.get(index)) ? -1 : 0;
      if (depth == 0) {
        return index;
      }
    }
    return -1;
  }

  /** The call that fills the range, when it is a dotted name followed by one argument list. */
  static Optional<Call> call(final List<String> tokens, final Range range) {
    final Range whole = unwrapped(tokens, range);
    final int open = calleeEnd(tokens, whole);
    if (open < 0 || closeOf(tokens, open) != whole.to() - 1) {
      return Optional.empty();
    }
    final Range inside = new Range(open + 1, whole.to() - 1);
    final List<Range> arguments =
        inside.to() > inside.from() ? split(tokens, inside, ",") : List.of();
    final String callee = String.join("", tokens.subList(whole.from(), open));
    return Optional.of(new Call(callee, arguments, whole, new Range(whole.from(), open)));
  }

  /** The position of the {@code (} after a dotted name that starts the range, or -1. */
  private static int calleeEnd(final List<String> tokens, final Range whole) {
    int index = whole.from();
    while (index < whole.to() && tokens.get(index).matches(NAME)) {
      index++;
      if (index >= whole.to() || !".".equals(tokens.get(index))) {
        break;
      }
      index++;
    }
    final boolean named = index > whole.from() && tokens.get(index - 1).matches(NAME);
    return named && index < whole.to() && "(".equals(tokens.get(index)) ? index : -1;
  }

  /** Whether the range is one name or a chain of names, calls and indexes with no operator. */
  static boolean mentions(final List<String> tokens, final Range range, final Set<String> names) {
    for (int index = range.from(); index < range.to(); index++) {
      if (names.contains(tokens.get(index)) && (index == 0 || !".".equals(tokens.get(index - 1)))) {
        return true;
      }
    }
    return false;
  }
}
