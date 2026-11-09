package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a function's parameter list: the positional names in order, whether it takes {@code
 * *args}, and how many positional parameters have a default value.
 */
record Parameters(List<String> positional, boolean varargs, int defaults) {

  private static final String STAR = "*";
  private static final String PARAMETERS = "parameters";
  private static final String TYPED_LIST = "typedargslist";
  private static final String EQUALS = "=";
  private static final String TYPED = "tfpdef";
  private static final String UNTYPED = "vfpdef";

  /** Reads the parameters of a function definition; positional names stop at the first star. */
  static Parameters of(final SyntaxNode definition) {
    final List<String> positional = new ArrayList<>();
    boolean varargs = false;
    int defaults = 0;
    boolean afterStar = false;
    for (final SyntaxNode part : listNodes(definition)) {
      final String text = SyntaxWalk.text(part);
      if (STAR.equals(text)) {
        afterStar = true;
        varargs = true;
      } else if (EQUALS.equals(text) && !afterStar) {
        defaults++;
      } else if ((TYPED.equals(part.kind()) || UNTYPED.equals(part.kind())) && !afterStar) {
        positional.add(SyntaxWalk.text(part.children().get(0)));
      }
    }
    return new Parameters(List.copyOf(positional), varargs, defaults);
  }

  /** The nodes of the parameter list of a function definition, in source order. */
  static List<SyntaxNode> listNodes(final SyntaxNode definition) {
    final List<SyntaxNode> nodes = new ArrayList<>();
    for (final SyntaxNode parameters : definition.children()) {
      if (PARAMETERS.equals(parameters.kind())) {
        for (final SyntaxNode list : parameters.children()) {
          if (TYPED_LIST.equals(list.kind())) {
            nodes.addAll(list.children());
          }
        }
      }
    }
    return nodes;
  }

  /** Every parameter name of a function, including {@code *args}, keyword-only and {@code **kw}. */
  static List<SyntaxNode> nameNodes(final SyntaxNode definition) {
    final List<SyntaxNode> names = new ArrayList<>();
    for (final SyntaxNode part : listNodes(definition)) {
      if (TYPED.equals(part.kind()) || UNTYPED.equals(part.kind())) {
        names.add(part.children().get(0));
      }
    }
    return names;
  }
}
