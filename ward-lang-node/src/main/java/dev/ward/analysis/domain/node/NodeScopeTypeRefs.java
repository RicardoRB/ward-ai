package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import java.util.List;
import java.util.Set;

/** Reads the type references of TypeScript and declares its type parameters. */
final class NodeScopeTypeRefs {

  private static final String TYPE_PARAMETER = "typeParameter";

  private static final String TYPE_QUERY = "typeQuery";

  private static final String TYPE_NAME = "typeName";
  private static final String PREDEFINED = "predefinedType";

  private final NodeScopeExpressions walk;

  NodeScopeTypeRefs(final NodeScopeExpressions walk) {
    this.walk = walk;
  }

  /** Handles a type name: the first identifier, which is the type or the namespace, is read. */
  void typeName(final SyntaxNode node) {
    final SyntaxNode name = NodeScopeNames.nameOf(NodeFlowSyntax.parts(node).get(0));
    if (name != null) {
      walk.read(name);
    }
  }

  /** Handles {@code typeof x} in a type position. */
  void typeQuery(final SyntaxNode node) {
    NodeLeaves.of(node).stream()
        .filter(NodeScopeNames::isName)
        .filter(leaf -> !"typeof".equals(NodeFlowSyntax.text(leaf)))
        .findFirst()
        .ifPresent(walk::read);
  }

  /**
   * Handles a built-in type such as {@code any}, read wherever a type can appear, including a cast.
   */
  void predefinedType(final SyntaxNode node) {
    NodeLeaves.of(node).stream().findFirst().ifPresent(walk::read);
  }

  /** Reads the type references under a type position, declaring and visiting nothing else. */
  void types(final SyntaxNode node) {
    if (TYPE_NAME.equals(node.kind())) {
      typeName(node);
    } else if (TYPE_QUERY.equals(node.kind())) {
      typeQuery(node);
    } else if (PREDEFINED.equals(node.kind())) {
      predefinedType(node);
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
    if (TYPE_PARAMETER.equals(node.kind())) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
      final SyntaxNode name = NodeScopeNames.nameOf(parts.get(0));
      if (name != null) {
        walk.builder.declare(
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
}
