package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * Checks a declared name against the style its node kind requires. A name is a declaration's child,
 * or the target of a plain assignment such as {@code x = 1}. Dunder names are not checked, and
 * leading underscores are ignored.
 */
final class IdentifierCase implements Measure {

  private static final String ASSIGN = "=";

  @Override
  public Optional<Violation> check(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final MetricRule rule) {
    final Optional<String> declared = declared(node, rule);
    if (declared.isEmpty() || skipped(declared.get())) {
      return Optional.empty();
    }
    final String bare = declared.get().replaceFirst("^_+", "");
    if (target.style().matches(bare)) {
      return Optional.empty();
    }
    return Optional.of(new Violation(node.span(), declared.get(), 0, target.style().description()));
  }

  private static boolean skipped(final String name) {
    return name.isEmpty()
        || name.length() > 1 && name.startsWith("__") && name.endsWith("__")
        || name.replaceFirst("^_+", "").isEmpty();
  }

  private static Optional<String> declared(final SyntaxNode node, final MetricRule rule) {
    final String name = rule.keys().name();
    if (!name.isEmpty() && SyntaxTrees.child(node, name).isPresent()) {
      return Optional.of(SyntaxTrees.nameOf(node, name));
    }
    return assignedName(node);
  }

  /** Returns the name when {@code node} is {@code name = value}, with a plain name as target. */
  private static Optional<String> assignedName(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() < 2 || !isAssign(children.get(1))) {
      return Optional.empty();
    }
    final String target = SyntaxTrees.text(children.get(0));
    return target.matches("[A-Za-z_][A-Za-z0-9_]*") ? Optional.of(target) : Optional.empty();
  }

  private static boolean isAssign(final SyntaxNode node) {
    return SyntaxTrees.isToken(node) && ASSIGN.equals(SyntaxTrees.text(node));
  }
}
