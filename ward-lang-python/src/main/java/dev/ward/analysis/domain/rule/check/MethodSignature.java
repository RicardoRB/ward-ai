package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parameters of a function as Pylint's {@code args} describes them: the positional parameters
 * (positional-only ones included), the keyword-only ones, and whether {@code *args} or {@code
 * **kwargs} is present.
 */
record MethodSignature(
    List<Param> positional, List<Param> keywordOnly, boolean varargs, boolean kwargs) {

  private static final String STAR = "*";
  private static final String DOUBLE_STAR = "**";
  private static final String SLASH = "/";
  private static final Pattern NAME = Pattern.compile("^\\w+");

  /** A parameter: its name and whether it has a default value. */
  record Param(String name, boolean hasDefault) {}

  /** Copies the lists. */
  MethodSignature {
    positional = List.copyOf(positional);
    keywordOnly = List.copyOf(keywordOnly);
  }

  /** Reads the parameters of a function definition, or returns empty when it has none. */
  static Optional<MethodSignature> of(final SyntaxNode function) {
    return FunctionBodies.child(function, SyntaxKinds.PARAMETERS)
        .map(parameters -> parse(SyntaxWalk.text(parameters)));
  }

  /** Parses the text of a parenthesized parameter list such as {@code (a, *b, c=1, **d)}. */
  static MethodSignature parse(final String parameters) {
    final String inside =
        parameters.length() >= 2 ? parameters.substring(1, parameters.length() - 1) : "";
    final List<Param> positional = new ArrayList<>();
    final List<Param> keywordOnly = new ArrayList<>();
    boolean afterStar = false;
    boolean varargs = false;
    boolean kwargs = false;
    for (final String item : ArgumentList.split(inside)) {
      if (SLASH.equals(item)) {
        continue;
      }
      if (item.startsWith(DOUBLE_STAR)) {
        kwargs = true;
      } else if (item.startsWith(STAR)) {
        afterStar = true;
        varargs = varargs || item.length() > STAR.length();
      } else if (afterStar) {
        keywordOnly.add(param(item));
      } else {
        positional.add(param(item));
      }
    }
    return new MethodSignature(positional, keywordOnly, varargs, kwargs);
  }

  private static Param param(final String item) {
    final Matcher name = NAME.matcher(item);
    return new Param(name.find() ? name.group() : "", item.contains("="));
  }

  /** The positional parameter names, in order. */
  List<String> positionalNames() {
    return positional.stream().map(Param::name).toList();
  }
}
