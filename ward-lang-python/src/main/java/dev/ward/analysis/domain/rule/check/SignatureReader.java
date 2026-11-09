package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the signature that a call binds to: the parameters of a function, or of the {@code
 * __init__} of a class that has no bases. A {@code /} marker makes the signature unknown.
 */
final class SignatureReader {

  private static final String FUNCTION = "funcdef";
  private static final String INIT = "__init__";
  private static final String PARAMETERS = "parameters";
  private static final String TYPED_LIST = "typedargslist";
  private static final String TYPED = "tfpdef";
  private static final String UNTYPED = "vfpdef";
  private static final String STAR = "*";
  private static final String DOUBLE_STAR = "**";
  private static final String SLASH = "/";
  private static final String COMMA = ",";

  /**
   * The parameters of a callee: the positional names, the keyword-only names and the catch-alls.
   */
  record Signature(
      List<String> positional, Set<String> keywordOnly, boolean varargs, boolean kwargs) {

    boolean accepts(final String keyword) {
      return kwargs || positional.contains(keyword) || keywordOnly.contains(keyword);
    }

    boolean filledByPosition(final String keyword, final int given) {
      return positional.subList(0, Math.min(given, positional.size())).contains(keyword);
    }
  }

  private SignatureReader() {}

  /** The signature a function definition has, or the one its constructor has for a class. */
  static Optional<Signature> of(final SyntaxNode definition) {
    if (FUNCTION.equals(definition.kind())) {
      return parameters(definition, false);
    }
    if (ModuleDefinitions.hasBases(definition)) {
      return Optional.empty();
    }
    return ModuleDefinitions.method(definition, INIT).flatMap(init -> parameters(init, true));
  }

  private static Optional<Signature> parameters(final SyntaxNode definition, final boolean method) {
    final ParameterScan scan = new ParameterScan();
    for (final SyntaxNode part : parameterNodes(definition)) {
      if (SLASH.equals(SyntaxWalk.text(part))) {
        return Optional.empty();
      }
      scan.accept(part);
    }
    return scan.signature(method);
  }

  /** The nodes of the parameter list: the children of its typed argument list. */
  private static List<SyntaxNode> parameterNodes(final SyntaxNode definition) {
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

  /** The state of one left-to-right scan over the parameter list. */
  private static final class ParameterScan {

    private final List<String> positional = new ArrayList<>();
    private final Set<String> keywordOnly = new HashSet<>();
    private boolean varargs;
    private boolean kwargs;
    private boolean afterStar;
    private boolean starName;
    private boolean doubleStarName;

    void accept(final SyntaxNode part) {
      final String text = SyntaxWalk.text(part);
      if (STAR.equals(text)) {
        afterStar = true;
        starName = true;
      } else if (DOUBLE_STAR.equals(text)) {
        doubleStarName = true;
      } else if (COMMA.equals(text)) {
        starName = false;
      } else if (TYPED.equals(part.kind()) || UNTYPED.equals(part.kind())) {
        name(SyntaxWalk.text(part.children().get(0)));
      }
    }

    private void name(final String name) {
      if (doubleStarName) {
        kwargs = true;
        doubleStarName = false;
      } else if (starName) {
        varargs = true;
        starName = false;
      } else if (afterStar) {
        keywordOnly.add(name);
      } else {
        positional.add(name);
      }
    }

    Optional<Signature> signature(final boolean method) {
      final List<String> names = new ArrayList<>(positional);
      if (method) {
        if (names.isEmpty()) {
          return Optional.empty();
        }
        names.remove(0);
      }
      return Optional.of(
          new Signature(List.copyOf(names), Set.copyOf(keywordOnly), varargs, kwargs));
    }
  }
}
