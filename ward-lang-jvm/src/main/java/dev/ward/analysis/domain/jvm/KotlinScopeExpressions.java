package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles the expressions of Kotlin: assignments, member access and names. */
final class KotlinScopeExpressions {

  private static final int TRIPLE = 3;
  private static final Set<String> CHAIN =
      Set.of(
          "expression",
          "disjunction",
          "conjunction",
          "equalityComparison",
          "comparison",
          "namedInfix",
          "elvisExpression",
          "infixFunctionCall",
          "rangeExpression",
          "additiveExpression",
          "multiplicativeExpression",
          "typeRHS",
          "prefixUnaryExpression",
          "postfixUnaryExpression",
          "atomicExpression");
  private static final String SIMPLE = "simpleIdentifier";
  private static final String EXPRESSION = "expression";

  private final KotlinScopeWalk walk;

  KotlinScopeExpressions(final KotlinScopeWalk walk) {
    this.walk = walk;
  }

  /** The name an expression chain is made of alone, or null. */
  static SyntaxNode plainName(final SyntaxNode expression) {
    SyntaxNode current = expression;
    while (CHAIN.contains(current.kind()) && current.children().size() == 1) {
      current = current.children().get(0);
    }
    return SIMPLE.equals(current.kind()) ? current : null;
  }

  void assignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == TRIPLE && "assignmentOperator".equals(parts.get(1).kind())) {
      target(parts.get(0), !"=".equals(KotlinScopeWalk.text(parts.get(1))));
      walk.visit(parts.get(2));
    } else {
      walk.generic(node);
    }
  }

  void target(final SyntaxNode expression, final boolean compound) {
    final SyntaxNode name = plainName(expression);
    if (name == null) {
      walk.visit(expression);
      return;
    }
    if (compound) {
      walk.builder.reference(KotlinScopeWalk.text(name), name.span(), ReferenceAccess.READ);
    }
    walk.builder.reference(KotlinScopeWalk.text(name), name.span(), ReferenceAccess.WRITE);
  }

  void postfix(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean update =
        parts.size() == 2
            && ("++".equals(KotlinScopeWalk.text(parts.get(1)))
                || "--".equals(KotlinScopeWalk.text(parts.get(1))))
            && plainName(parts.get(0)) != null;
    if (update) {
      target(parts.get(0), true);
    } else {
      walk.generic(node);
    }
  }

  void prefix(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean update =
        parts.size() == 2
            && ("++".equals(KotlinScopeWalk.text(parts.get(0)))
                || "--".equals(KotlinScopeWalk.text(parts.get(0))))
            && plainName(parts.get(1)) != null;
    if (update) {
      target(parts.get(1), true);
    } else {
      walk.generic(node);
    }
  }

  /** A member access {@code a.b}: the name after the dot is not a reference. */
  void operation(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (!parts.isEmpty()
        && "memberAccessOperator".equals(parts.get(0).kind())
        && parts.size() > 1) {
      final SyntaxNode member = parts.get(parts.size() - 1);
      member.children().stream().skip(1).forEach(walk::visit);
    } else {
      walk.generic(node);
    }
  }

  void atomic(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 1 && SIMPLE.equals(parts.get(0).kind())) {
      walk.builder.reference(
          KotlinScopeWalk.text(parts.get(0)), parts.get(0).span(), ReferenceAccess.READ);
    } else if (parts.size() == 2 && "val".equals(KotlinScopeWalk.text(parts.get(0)))) {
      walk.declare(parts.get(1), BindingKind.CONSTANT, true, EnumSet.noneOf(BindingFlag.class));
    } else {
      walk.generic(node);
    }
  }

  void argument(final SyntaxNode node) {
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(walk::visit);
  }
}
