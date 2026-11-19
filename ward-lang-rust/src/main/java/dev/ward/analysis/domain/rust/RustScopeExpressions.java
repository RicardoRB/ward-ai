package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.List;

/**
 * The names that expressions read and write: a {@code let} condition, an assignment, a member
 * access and the fields of a struct literal.
 */
final class RustScopeExpressions {
  /** An operand, an operator and an operand. */
  private static final int OPERATION = 3;

  /** The first part of an operation that follows the operand and the operator. */
  private static final int AFTER_OPERATOR = 2;

  private static final int ONLY = 1;

  private final RustScopeWalk walk;

  RustScopeExpressions(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** An expression: a {@code let} condition, an assignment or a member access reads differently. */
  void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.isEmpty()) {
      return;
    }
    final boolean operation = parts.size() >= OPERATION;
    if (RustTrees.isToken(parts.get(0), RustKinds.LET)) {
      letCondition(node);
    } else if (parts.size() == OPERATION && RustTrees.isToken(parts.get(1), RustKinds.EQUALS)) {
      assignment(parts, false);
    } else if (parts.size() == OPERATION
        && RustKinds.COMPOUND_ASSIGN.equals(parts.get(1).kind())) {
      assignment(parts, true);
    } else if (operation && RustTrees.isToken(parts.get(1), RustKinds.DOT)) {
      member(parts);
    } else {
      walk.generic(node);
    }
  }

  /** {@code let PAT = VALUE} as a condition: the value is read, then the names exist. */
  private void letCondition(final SyntaxNode node) {
    final SyntaxNode pattern = RustTrees.child(node, RustKinds.PATTERN);
    final SyntaxNode value = node.children().get(node.children().size() - 1);
    walk.visit(value);
    walk.scopes.openAfter(value.span());
    walk.patterns.bind(pattern, BindingKind.CONSTANT, true);
  }

  private void assignment(final List<SyntaxNode> parts, final boolean compound) {
    walk.visit(parts.get(AFTER_OPERATOR));
    final SyntaxNode name = plainName(parts.get(0));
    if (name == null) {
      walk.visit(parts.get(0));
      return;
    }
    final String text = RustTrees.name(name);
    if (compound) {
      walk.builder.reference(text, name.span(), ReferenceAccess.READ);
    }
    walk.builder.reference(text, name.span(), ReferenceAccess.WRITE);
  }

  /** The identifier of an expression that is only a one-segment path, or null. */
  private static SyntaxNode plainName(final SyntaxNode expression) {
    if (expression.children().size() != ONLY) {
      return null;
    }
    final SyntaxNode path = RustTrees.child(expression.children().get(0), "pathInExpression");
    if (path == null || path.children().size() != ONLY) {
      return null;
    }
    final SyntaxNode segment =
        RustTrees.child(path.children().get(0), RustKinds.PATH_IDENT_SEGMENT);
    return segment == null ? null : RustTrees.child(segment, RustKinds.IDENTIFIER);
  }

  /** A field, method call, tuple index or {@code .await}: the member name is not a variable. */
  private void member(final List<SyntaxNode> parts) {
    walk.visit(parts.get(0));
    for (final SyntaxNode part : parts.subList(AFTER_OPERATOR, parts.size())) {
      if (RustKinds.PATH_EXPR_SEGMENT.equals(part.kind())) {
        part.children().stream()
            .filter(child -> RustKinds.GENERIC_ARGS.equals(child.kind()))
            .forEach(walk::visit);
      } else if (!isMemberName(part)) {
        walk.visit(part);
      }
    }
  }

  private static boolean isMemberName(final SyntaxNode part) {
    return RustKinds.IDENTIFIER.equals(part.kind())
        || RustKinds.TUPLE_INDEX.equals(part.kind())
        || RustTrees.isToken(part, RustKinds.AWAIT);
  }

  /** A field of a struct literal: {@code x} alone reads the variable {@code x}. */
  void fieldInit(final SyntaxNode node) {
    final boolean explicit =
        node.children().stream().anyMatch(child -> RustTrees.isToken(child, RustKinds.COLON));
    for (final SyntaxNode child : node.children()) {
      if (RustKinds.IDENTIFIER.equals(child.kind())) {
        if (!explicit) {
          walk.builder.reference(RustTrees.name(child), child.span(), ReferenceAccess.READ);
        }
      } else if (!RustKinds.TUPLE_INDEX.equals(child.kind())) {
        walk.visit(child);
      }
    }
  }
}
