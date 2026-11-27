package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Tells whether a method checks the result of a call, so that a dereference of it is safe. */
final class NullLibraryGuards {
  private static final Set<String> CHECKS = Set.of("requireNonNull", "nonNull", "isNull");
  private static final Set<String> MEMBERSHIP = Set.of("containsKey", "containsValue", "contains");

  private NullLibraryGuards() {}

  /**
   * Tells whether {@code method} compares the call's text with null, passes it to a null check, or
   * asks its receiver whether it holds a key.
   */
  static boolean checked(final NullFlow.Call call, final NullModel.Method method) {
    final String text = SyntaxWalk.text(call.node());
    for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
      final List<SyntaxNode> parts = expression.children();
      final String operator = NullSyntax.operator(expression);
      if (("==".equals(operator) || "!=".equals(operator))
          && (SyntaxWalk.text(parts.get(0)).equals(text)
              || SyntaxWalk.text(parts.get(2)).equals(text))) {
        return true;
      }
      if (NullFlow.call(expression).filter(other -> asks(other, call, text)).isPresent()) {
        return true;
      }
    }
    return false;
  }

  private static boolean asks(
      final NullFlow.Call other,
      final NullFlow.Call call,
      final String text
  ) {
    final boolean membership =
        MEMBERSHIP.contains(other.name()) && other.receiver().equals(call.receiver());
    final boolean checked =
        CHECKS.contains(other.name())
            && !other.args().isEmpty()
            && SyntaxWalk.text(other.args().get(0)).equals(text);
    return membership || checked;
  }
}
