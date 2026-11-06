package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
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

  private static final Set<String> MEMBER_OPERATORS = Set.of(".", "?.");
  private static final Set<String> UPDATE_OPERATORS = Set.of("++", "--");
  private static final String EQUALS = "=";
  private static final String EVAL = "eval";

  private final ScopeBuilder builder;
  private final NodeScopeNames names;
  private final Consumer<SyntaxNode> visit;

  NodeScopeExpressions(
      final ScopeBuilder builder, final NodeScopeNames names, final Consumer<SyntaxNode> visit) {
    this.builder = builder;
    this.names = names;
    this.visit = visit;
  }

  /** Handles a {@code singleExpression}. */
  void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (parts.size() == 1) {
      final SyntaxNode name = NodeScopeNames.nameOf(node);
      if (name == null) {
        visit.accept(parts.get(0));
      } else {
        read(name);
      }
    } else if (parts.size() == 3 && isAssignment(parts.get(1))) {
      names.target(parts.get(0), !EQUALS.equals(NodeFlowSyntax.text(parts.get(1))));
      visit.accept(parts.get(2));
    } else if (parts.size() == 2 && updates(parts)) {
      names.target(parts.get(0).children().isEmpty() ? parts.get(1) : parts.get(0), true);
    } else if (parts.size() == 3 && MEMBER_OPERATORS.contains(NodeFlowSyntax.text(parts.get(1)))) {
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

  private void read(final SyntaxNode name) {
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

  /** Handles a type name: the first identifier, which is the type or the namespace, is read. */
  void typeName(final SyntaxNode node) {
    final SyntaxNode name = NodeScopeNames.nameOf(NodeFlowSyntax.parts(node).get(0));
    if (name != null) {
      read(name);
    }
  }

  /** Handles {@code typeof x} in a type position. */
  void typeQuery(final SyntaxNode node) {
    NodeLeaves.of(node).stream()
        .filter(NodeScopeNames::isName)
        .filter(leaf -> !"typeof".equals(NodeFlowSyntax.text(leaf)))
        .findFirst()
        .ifPresent(this::read);
  }

  /** Reads the type references under a type position, declaring and visiting nothing else. */
  void types(final SyntaxNode node) {
    if ("typeName".equals(node.kind())) {
      typeName(node);
    } else if ("typeQuery".equals(node.kind())) {
      typeQuery(node);
    } else if (!"typeParameters".equals(node.kind())) {
      node.children().forEach(this::types);
    }
  }

  /** Declares the type parameters among {@code parts} in the current scope. */
  void typeParameters(final List<SyntaxNode> parts) {
    for (final SyntaxNode list : NodeFlowSyntax.allOf(parts, "typeParameters")) {
      declareTypeParameters(list);
    }
  }

  /** Declares the type parameters of a {@code typeParameters} node in the current scope. */
  void declareTypeParameters(final SyntaxNode node) {
    if ("typeParameter".equals(node.kind())) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
      final SyntaxNode name = NodeScopeNames.nameOf(parts.get(0));
      if (name != null) {
        builder.declare(
            NodeFlowSyntax.text(name),
            BindingKind.TYPE,
            name.span(),
            Set.of(BindingFlag.TYPE_ONLY));
      }
      parts.stream().skip(1).forEach(this::types);
    } else {
      node.children().forEach(this::declareTypeParameters);
    }
  }

  /**
   * Handles a declaration statement without {@code var}, {@code let} or {@code const}. The
   * TypeScript grammar reads {@code type A = B} and {@code export { a }} this way, so a leading
   * {@code type} declares a type and any other name is read.
   */
  void undeclared(final SyntaxNode statement) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(statement);
    final SyntaxNode list = NodeFlowSyntax.firstOf(parts, "variableDeclarationList");
    for (final SyntaxNode declaration :
        list == null
            ? List.<SyntaxNode>of()
            : NodeFlowSyntax.allOf(NodeFlowSyntax.parts(list), "variableDeclaration")) {
      final List<SyntaxNode> inner = NodeFlowSyntax.parts(declaration);
      if (isTypeAlias(inner)) {
        declareAlias(inner.get(1));
      } else {
        inner.forEach(part -> readAll(part));
      }
    }
  }

  private static boolean isTypeAlias(final List<SyntaxNode> inner) {
    return inner.size() >= 2
        && "type".equals(NodeFlowSyntax.text(inner.get(0)))
        && "singleExpression".equals(inner.get(1).kind());
  }

  private void declareAlias(final SyntaxNode body) {
    NodeLeaves.of(body).stream()
        .filter(NodeScopeNames::isName)
        .findFirst()
        .ifPresent(
            name ->
                builder.declare(
                    NodeFlowSyntax.text(name),
                    BindingKind.TYPE,
                    name.span(),
                    Set.of(BindingFlag.TYPE_ONLY)));
  }

  private void readAll(final SyntaxNode node) {
    final SyntaxNode name = NodeScopeNames.nameOf(node);
    if (name != null) {
      read(name);
    } else if ("typeAnnotation".equals(node.kind())) {
      types(node);
    } else {
      visit.accept(node);
    }
  }
}
