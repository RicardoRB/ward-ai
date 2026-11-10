package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.List;
import java.util.Set;

/** Handles the expressions of Java: assignments, patterns and names. */
final class JavaScopeExpressions {

  private static final int PATTERN_NAMES = 1;

  private static final String EXPRESSION_LIST = "expressionList";

  private static final int TRIPLE = 3;
  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "&=", "|=", "^=", ">>=", ">>>=", "<<=", "%=");
  private static final Set<String> UPDATES = Set.of("++", "--");
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";

  private final JavaScopeWalk walk;

  JavaScopeExpressions(final JavaScopeWalk walk) {
    this.walk = walk;
  }

  void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == TRIPLE && ASSIGNMENTS.contains(JavaScopeWalk.text(parts.get(1)))) {
      target(parts.get(0), !"=".equals(JavaScopeWalk.text(parts.get(1))));
      walk.visit(parts.get(2));
    } else if (parts.size() == 2 && updates(parts)) {
      target(EXPRESSION.equals(parts.get(0).kind()) ? parts.get(0) : parts.get(1), true);
    } else if (parts.size() == TRIPLE && "instanceof".equals(JavaScopeWalk.text(parts.get(1)))) {
      walk.visit(parts.get(0));
      patternBinding(parts.get(2));
    } else {
      walk.generic(node);
    }
  }

  static boolean updates(final List<SyntaxNode> parts) {
    return UPDATES.contains(JavaScopeWalk.text(parts.get(0)))
        || UPDATES.contains(JavaScopeWalk.text(parts.get(1)));
  }

  /** The name an expression is made of alone, or null. */
  static SyntaxNode plainName(final SyntaxNode expression) {
    SyntaxNode current = expression;
    while (current.children().size() == 1 && !IDENTIFIER.equals(current.kind())) {
      current = current.children().get(0);
    }
    return IDENTIFIER.equals(current.kind()) ? current : null;
  }

  void target(final SyntaxNode expression, final boolean compound) {
    final SyntaxNode name = plainName(expression);
    if (name == null) {
      walk.visit(expression);
      return;
    }
    if (compound) {
      walk.builder.reference(JavaScopeWalk.text(name), name.span(), ReferenceAccess.READ);
    }
    walk.builder.reference(JavaScopeWalk.text(name), name.span(), ReferenceAccess.WRITE);
  }

  /**
   * {@code o instanceof String s} binds {@code s}; the grammar reads it as a type with two names.
   */
  void patternBinding(final SyntaxNode type) {
    final SyntaxNode pattern = findKind(type, "classType");
    if (pattern == null) {
      return;
    }
    final List<SyntaxNode> names = JavaSyntaxNodes.children(pattern, "typeIdentifier");
    if (names.size() > PATTERN_NAMES) {
      final SyntaxNode name = names.get(names.size() - 1);
      walk.types.declare(name, BindingKind.VARIABLE, false, false, true);
    }
  }

  static SyntaxNode findKind(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = findKind(child, kind);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  void primary(final SyntaxNode node) {
    final SyntaxNode name = node.children().size() == 1 ? JavaScopeWalk.identifierOf(node) : null;
    if (name == null) {
      walk.generic(node);
    } else {
      walk.builder.reference(JavaScopeWalk.text(name), name.span(), ReferenceAccess.READ);
    }
  }

  void methodCall(final SyntaxNode node) {
    node.children().stream().filter(part -> !IDENTIFIER.equals(part.kind())).forEach(walk::visit);
  }

  void switchRule(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if (EXPRESSION_LIST.equals(part.kind())) {
        part.children().stream()
            .filter(label -> !EXPRESSION.equals(label.kind()) || plainName(label) == null)
            .forEach(walk::visit);
      } else if (EXPRESSION.equals(part.kind()) && plainName(part) != null) {
        continue;
      } else {
        walk.visit(part);
      }
    }
  }
}
