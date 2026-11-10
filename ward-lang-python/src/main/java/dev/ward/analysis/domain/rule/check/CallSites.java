package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads a call such as {@code f(1, b=2)} that names its callee directly, and its arguments. */
final class CallSites {

  private static final String ATOM = "atom";
  private static final String NAME = "name";
  private static final String TRAILER = "trailer";
  private static final String ARGLIST = "arglist";
  private static final String ARGUMENT = "argument";
  private static final String OPEN = "(";
  private static final String EQUALS = "=";
  private static final String STAR = "*";
  private static final String DOUBLE_STAR = "**";
  private static final int CALL_PARTS = 2;
  private static final int KEYWORD_PARTS = 3;

  /** The arguments of one call: the plain positional count and the keyword names. */
  record Arguments(int positional, List<String> keywords, boolean unpacks) {}

  private CallSites() {}

  /**
   * The name of a call of the form {@code name(...)} with one call trailer, if the call has that
   * form.
   */
  static Optional<String> calleeName(final SyntaxNode call) {
    final List<SyntaxNode> parts = call.children();
    if (parts.size() != CALL_PARTS
        || !ATOM.equals(parts.get(0).kind())
        || !TRAILER.equals(parts.get(1).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> atom = parts.get(0).children();
    final List<SyntaxNode> trailer = parts.get(1).children();
    if (atom.size() != 1
        || !NAME.equals(atom.get(0).kind())
        || trailer.isEmpty()
        || !OPEN.equals(SyntaxWalk.text(trailer.get(0)))) {
      return Optional.empty();
    }
    return Optional.of(SyntaxWalk.text(atom.get(0)));
  }

  /** The arguments of a call, with each argument classified as positional, keyword or unpacked. */
  static Arguments arguments(final SyntaxNode call) {
    final List<String> kinds = new ArrayList<>();
    for (final SyntaxNode part : call.children().get(1).children()) {
      if (ARGLIST.equals(part.kind())) {
        part.children().stream()
            .filter(argument -> ARGUMENT.equals(argument.kind()) && !argument.children().isEmpty())
            .forEach(argument -> kinds.add(classify(argument)));
      }
    }
    final List<String> keywords =
        kinds.stream().filter(kind -> kind != null && !kind.isEmpty()).toList();
    final int positional =
        (int) kinds.stream().filter(kind -> kind != null && kind.isEmpty()).count();
    return new Arguments(positional, keywords, kinds.contains(null));
  }

  /** Null for an unpacked argument, the name for a keyword, and empty for a positional one. */
  private static String classify(final SyntaxNode argument) {
    final List<SyntaxNode> parts = argument.children();
    final String first = SyntaxWalk.text(parts.get(0));
    if (STAR.equals(first) || DOUBLE_STAR.equals(first)) {
      return null;
    }
    final boolean keyword =
        parts.size() == KEYWORD_PARTS && EQUALS.equals(SyntaxWalk.text(parts.get(1)));
    return keyword ? first : "";
  }
}
