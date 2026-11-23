package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks a declared name against the style its node kind requires. A name is a declaration's child,
 * or the target of a plain assignment such as {@code x = 1}. Dunder names, operators and the names
 * the rule exempts are not checked, and leading underscores are ignored.
 */
final class IdentifierCase implements Measure {
  private static final String ASSIGN = "=";
  private static final int ASSIGNMENT_PARTS = 3;
  private static final String BACKTICK = "`";
  private static final String NAME = "[A-Za-z_][\\w.]*";
  // A subscript or a union of names, or a call that declares a type, written without spaces.
  private static final Pattern TYPE_ALIAS =
      Pattern.compile(
          "^" + NAME + "\\[.*\\]$|^" + NAME + "(?:\\|" + NAME + ")+$"
              + "|^(?:typing\\.)?(?:TypeVar|NewType|TypeAlias)\\("
      );

  @Override
  public Optional<Violation> check(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final MetricRule rule
  ) {
    final Optional<String> declared = declared(node, rule);
    if (declared.isEmpty()
        || skipped(declared.get())
        || rule.keys().exemptNames().contains(declared.get())) {
      return Optional.empty();
    }
    if (rule.keys().exemptTypeAliases() && typeAlias(node)) {
      return Optional.empty();
    }
    final String bare = declared.get().replace(BACKTICK, "").replaceFirst("^_+", "");
    if (target.style().matches(bare)) {
      return Optional.empty();
    }
    return Optional.of(new Violation(node.span(), declared.get(), 0, target.style().description()));
  }

  /** Tells whether {@code node} is {@code name = value} with a type as its value. */
  private static boolean typeAlias(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    return children.size() == ASSIGNMENT_PARTS
        && isAssign(children.get(1))
        && TYPE_ALIAS.matcher(SyntaxTrees.text(children.get(2))).find();
  }

  private static boolean skipped(final String name) {
    return name.isEmpty()
        || isOperator(name)
        || name.length() > 1 && name.startsWith("__") && name.endsWith("__")
        || name.replaceFirst("^_+", "").isEmpty();
  }

  /** An operator such as {@code ==} is a symbol, not an identifier with a case. */
  private static boolean isOperator(final String name) {
    final char first = name.charAt(0);
    return !Character.isLetter(first) && first != '_' && !BACKTICK.equals(String.valueOf(first));
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
