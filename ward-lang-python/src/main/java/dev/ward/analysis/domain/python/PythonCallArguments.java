package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the arguments of a Python call trailer. */
final class PythonCallArguments {
  private static final String OPEN_LIST = "[";
  private static final String CLOSE_LIST = "]";
  private static final String OPEN_TUPLE = "(";
  private static final String CLOSE_TUPLE = ")";

  private PythonCallArguments() {}

  /** The values of the arguments of a call trailer, in order; a keyword's value is the argument. */
  static List<SyntaxNode> values(final SyntaxNode trailer) {
    final List<SyntaxNode> values = new ArrayList<>();
    for (final SyntaxNode list : PythonSyntax.children(trailer, "arglist")) {
      for (final SyntaxNode argument : PythonSyntax.children(list, "argument")) {
        values.add(valueOf(argument));
      }
    }
    return values;
  }

  private static SyntaxNode valueOf(final SyntaxNode argument) {
    final List<SyntaxNode> children = argument.children();
    final boolean prefixed = children.size() > 1 && children.get(0).children().isEmpty();
    final boolean keyword = children.size() > 1 && PythonSyntax.isToken(children.get(1), "=");
    return prefixed || keyword ? children.get(children.size() - 1) : children.get(0);
  }

  /** Whether the call trailer passes {@code name=value}, the value being one token. */
  static boolean passes(final SyntaxNode trailer, final String name, final String value) {
    return PythonSyntax.children(trailer, "arglist").stream()
        .flatMap(list -> PythonSyntax.children(list, "argument").stream())
        .anyMatch(argument -> isKeyword(argument.children(), name, value));
  }

  private static boolean isKeyword(
      final List<SyntaxNode> children,
      final String name,
      final String value
  ) {
    return children.size() == PythonSyntax.THREE_PARTS
        && name.equals(PythonSyntax.firstText(children.get(0)))
        && PythonSyntax.isToken(children.get(1), "=")
        && PythonSyntax.tokens(children.get(2)).size() == 1
        && value.equals(PythonSyntax.firstText(children.get(2)));
  }

  /** Whether an argument value is a list display, or a tuple display with a comma. */
  static boolean isSequenceDisplay(final SyntaxNode value) {
    final List<SyntaxNode> tokens = PythonSyntax.tokens(value);
    final String first = PythonSyntax.firstText(value);
    final String last = tokens.isEmpty() ? "" : PythonSyntax.text(tokens.get(tokens.size() - 1));
    final boolean list = OPEN_LIST.equals(first) && CLOSE_LIST.equals(last);
    return list || isTuple(value, first, last);
  }

  private static boolean isTuple(final SyntaxNode value, final String first, final String last) {
    return OPEN_TUPLE.equals(first)
        && CLOSE_TUPLE.equals(last)
        && PythonSyntax.firstOfKind(value, "testlist_comp")
            .filter(items -> PythonSyntax.indexOfToken(items.children(), ",") >= 0)
            .isPresent();
  }

  static boolean opens(final SyntaxNode trailer, final String token) {
    return !trailer.children().isEmpty() && PythonSyntax.isToken(trailer.children().get(0), token);
  }

  /** Whether a call of {@code module} gives a list or tuple display as the program and arguments. */
  static boolean isVector(
      final String module,
      final List<SyntaxNode> values,
      final SyntaxNode trailer
  ) {
    return "subprocess".equals(module)
        && !values.isEmpty()
        && isSequenceDisplay(values.get(0))
        && !passes(trailer, "shell", "True");
  }

  /**
   * The spans of the arguments. A list or tuple given to a {@code subprocess} function without
   * {@code shell=True} is the program and its arguments, which no shell reads, so it stands for no
   * command text: its span is empty. shortcut: the program named by the vector's first item is not
   * followed (the argument-vector site reports it as argument injection).
   */
  static List<Span> spans(
      final String module,
      final List<SyntaxNode> values,
      final SyntaxNode trailer
  ) {
    final List<Span> spans = new ArrayList<>(values.stream().map(SyntaxNode::span).toList());
    if (isVector(module, values, trailer)) {
      final Span vector = spans.get(0);
      spans.set(
          0,
          new Span(
              vector.startLine(),
              vector.startColumn(),
              vector.startLine(),
              vector.startColumn(),
              vector.startOffset(),
              vector.startOffset()
          )
      );
    }
    return spans;
  }
}
