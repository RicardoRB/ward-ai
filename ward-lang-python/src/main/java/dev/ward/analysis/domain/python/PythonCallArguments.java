package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the arguments of a Python call trailer. */
final class PythonCallArguments {

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

  static boolean opens(final SyntaxNode trailer, final String token) {
    return !trailer.children().isEmpty() && PythonSyntax.isToken(trailer.children().get(0), token);
  }
}
