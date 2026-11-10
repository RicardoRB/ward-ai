package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Records the name references of expressions and of type positions: a name is read where an
 * expression is a name alone, written where it is an assignment target, and read where it is a type
 * reference. Member names and property keys are never references.
 */
final class NodeScopeExpressions {

  private static final int TRIPLE = 3;
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final int PAIR = 2;
  private static final int SINGLE = 1;

  private static final Set<String> MEMBER_OPERATORS = Set.of(".", "?.");
  private static final Set<String> UPDATE_OPERATORS = Set.of("++", "--");
  private static final String EQUALS = "=";
  private static final String EVAL = "eval";

  final ScopeBuilder builder;
  private final NodeScopeNames names;
  private final Consumer<SyntaxNode> visit;
  final NodeScopeTypeRefs typeRefs;

  NodeScopeExpressions(
      final ScopeBuilder builder, final NodeScopeNames names, final Consumer<SyntaxNode> visit) {
    this.typeRefs = new NodeScopeTypeRefs(this);
    this.builder = builder;
    this.names = names;
    this.visit = visit;
  }

  /** Handles a {@code singleExpression}. */
  void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (parts.size() == SINGLE) {
      single(node, parts.get(0));
    } else if (parts.size() == TRIPLE) {
      triple(parts);
    } else if (parts.size() == PAIR && updates(parts)) {
      names.target(parts.get(0).children().isEmpty() ? parts.get(1) : parts.get(0), true);
    } else {
      parts.forEach(visit);
    }
  }

  private void single(final SyntaxNode node, final SyntaxNode only) {
    final SyntaxNode name = NodeScopeNames.nameOf(node);
    if (name == null) {
      visit.accept(only);
    } else {
      read(name);
    }
  }

  private void triple(final List<SyntaxNode> parts) {
    final String operator = NodeFlowSyntax.text(parts.get(1));
    if (isAssignment(parts.get(1))) {
      names.target(parts.get(0), !EQUALS.equals(operator));
      visit.accept(parts.get(2));
    } else if (MEMBER_OPERATORS.contains(operator)) {
      visit.accept(parts.get(0));
    } else {
      parts.forEach(visit);
    }
  }

  private static boolean isAssignment(final SyntaxNode part) {
    return NodeSyntax.isAssignment(part);
  }

  private static boolean updates(final List<SyntaxNode> parts) {
    return UPDATE_OPERATORS.contains(NodeFlowSyntax.text(parts.get(0)))
        || UPDATE_OPERATORS.contains(NodeFlowSyntax.text(parts.get(1)));
  }

  void read(final SyntaxNode name) {
    final String text = NodeFlowSyntax.text(name);
    builder.reference(text, name.span(), ReferenceAccess.READ);
    if (EVAL.equals(text)) {
      builder.markOpen(builder.current());
    }
  }

  /** Handles {@code export { a as b }}: the first name is a local name that is read. */
  void exportedName(final SyntaxNode node) {
    final SyntaxNode first = NodeFlowSyntax.parts(node).get(0);
    final SyntaxNode name = NodeScopeNames.nameOf(first);
    if (name != null) {
      read(name);
    }
  }

  /**
   * Handles a declaration statement without {@code var}, {@code let} or {@code const}. The
   * TypeScript grammar reads {@code x = 1} this way, so a name with a value is written and any
   * other name is read. Type aliases are their own statement kind.
   */
  void undeclared(final SyntaxNode statement) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(statement);
    final SyntaxNode list = NodeFlowSyntax.firstOf(parts, "variableDeclarationList");
    for (final SyntaxNode declaration :
        list == null
            ? List.<SyntaxNode>of()
            : NodeFlowSyntax.allOf(NodeFlowSyntax.parts(list), "variableDeclaration")) {
      final List<SyntaxNode> inner = NodeFlowSyntax.parts(declaration);
      if (isAssignment(inner)) {
        names.target(inner.get(0), false);
        inner.stream().skip(1).forEach(visit);
      } else {
        inner.forEach(this::readAll);
      }
    }
  }

  /** {@code name = value} as a statement: the grammar reads it as a declaration without keyword. */
  private static boolean isAssignment(final List<SyntaxNode> inner) {
    return inner.size() >= 2
        && NodeScopeNames.nameOf(inner.get(0)) != null
        && "singleExpression".equals(inner.get(inner.size() - 1).kind());
  }

  private void readAll(final SyntaxNode node) {
    final SyntaxNode name = NodeScopeNames.nameOf(node);
    if (name != null) {
      read(name);
    } else if (TYPE_ANNOTATION.equals(node.kind())) {
      typeRefs.types(node);
    } else {
      visit.accept(node);
    }
  }
}
