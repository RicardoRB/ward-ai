package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decorators of a function that exempt it from the self rule. */
final class FunctionDecorators {
  private static final Set<String> EXEMPT_DECORATORS = Set.of("staticmethod", "classmethod");

  private static final String DECORATOR = "decorator";

  private FunctionDecorators() {}

  static boolean exemptDecorator(final List<SyntaxNode> ancestors) {
    final int from = Math.max(0, ancestors.size() - 2);
    for (final SyntaxNode node : ancestors.subList(from, ancestors.size())) {
      if (SyntaxKinds.DECORATED.equals(node.kind())) {
        return decoratorNames(node).stream().anyMatch(FunctionDecorators::exempt);
      }
    }
    return false;
  }

  /**
   * The names of the decorators of a {@code decorated} node, without arguments. They are read from
   * the tree, so a decorator that follows one with arguments is seen too.
   */
  static List<String> decoratorNames(final SyntaxNode decorated) {
    return FunctionBodies.ownScope(decorated, Set.of(DECORATOR)).stream()
        .map(decorator -> SyntaxWalk.text(decorator).substring(1).replaceAll("(?s)[\\s(].*", ""))
        .toList();
  }

  static boolean exempt(final String decorator) {
    return EXEMPT_DECORATORS.contains(decorator.toLowerCase(Locale.ROOT))
        || EXEMPT_DECORATORS.contains(decorator.replaceAll("^.*\\.", ""));
  }
}
