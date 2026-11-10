package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decorators of a function that exempt it from the self rule. */
final class FunctionDecorators {

  private static final Set<String> EXEMPT_DECORATORS = Set.of("staticmethod", "classmethod");

  private FunctionDecorators() {}

  static boolean exemptDecorator(final List<SyntaxNode> ancestors) {
    final int from = Math.max(0, ancestors.size() - 2);
    for (final SyntaxNode node : ancestors.subList(from, ancestors.size())) {
      if (SyntaxKinds.DECORATED.equals(node.kind())) {
        return decoratorLines(SyntaxWalk.text(node)).stream().anyMatch(FunctionDecorators::exempt);
      }
    }
    return false;
  }

  static List<String> decoratorLines(final String decorated) {
    final List<String> names = new ArrayList<>();
    for (final String line : decorated.split("\n")) {
      final String trimmed = line.strip();
      if (trimmed.startsWith("def") || trimmed.startsWith("async") || trimmed.startsWith("class")) {
        break;
      }
      if (trimmed.startsWith("@")) {
        names.add(trimmed.substring(1).replaceAll("[\\s(].*", ""));
      }
    }
    return names;
  }

  static boolean exempt(final String decorator) {
    return EXEMPT_DECORATORS.contains(decorator.toLowerCase(Locale.ROOT))
        || EXEMPT_DECORATORS.contains(decorator.replaceAll("^.*\\.", ""));
  }
}
