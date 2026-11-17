package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;
import java.util.Set;

/** Reads the type references of TypeScript and declares its type parameters. */
final class NodeScopeTypeRefs {
  private static final String TYPE_PARAMETER = "typeParameter";

  private static final String TYPE_QUERY = "typeQuery";

  private static final String TYPE_NAME = "typeName";
  private static final String PREDEFINED = "predefinedType";
  private static final String TYPE_PARAMETERS = "typeParameters";
  private static final String INFER = "inferType";
  private static final String CONDITIONAL = "conditionalType";
  private static final String MAPPED = "mappedType";
  private static final Set<String> SIGNATURES =
      Set.of("callSignature", "functionType", "constructorType");

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
    NodeLeaves.of(node).stream()
        .findFirst()
        .filter(NodeScopeNames::isName)
        .filter(leaf -> !"unique".equals(NodeFlowSyntax.text(leaf)))
        .ifPresent(walk::read);
  }

  /** Reads the type references under a type position, declaring and visiting nothing else. */
  void types(final SyntaxNode node) {
    if (TYPE_NAME.equals(node.kind())) {
      typeName(node);
    } else if (TYPE_QUERY.equals(node.kind())) {
      typeQuery(node);
    } else if (PREDEFINED.equals(node.kind())) {
      predefinedType(node);
    } else if (SIGNATURES.contains(node.kind())) {
      signature(node);
    } else if (INFER.equals(node.kind())) {
      declareInferred(node);
    } else if (CONDITIONAL.equals(node.kind())) {
      conditional(node);
    } else if (MAPPED.equals(node.kind())) {
      mapped(node);
    } else if (!TYPE_PARAMETERS.equals(node.kind())) {
      node.children().forEach(this::types);
    }
  }

  /** {@code infer X} declares the type variable {@code X} for the true branch of its condition. */
  private void declareInferred(final SyntaxNode node) {
    NodeLeaves.of(node).stream()
        .filter(NodeScopeNames::isName)
        .filter(leaf -> !"infer".equals(NodeFlowSyntax.text(leaf)))
        .findFirst()
        .ifPresent(
            name ->
                walk.builder.declare(
                    NodeFlowSyntax.text(name),
                    BindingKind.TYPE,
                    name.span(),
                    Set.of(BindingFlag.TYPE_ONLY)
                )
        );
  }

  /** A conditional type owns the variables its {@code infer} clauses declare. */
  private void conditional(final SyntaxNode node) {
    walk.builder.open(ScopeKind.FUNCTION, node.span());
    node.children().forEach(this::types);
    walk.builder.close();
  }

  /** {@code [K in keyof T]}: a mapped type declares the key variable for its own members. */
  private void mapped(final SyntaxNode node) {
    walk.builder.open(ScopeKind.FUNCTION, node.span());
    final SyntaxNode key =
        NodeFlowSyntax.parts(node).stream()
            .filter(part -> "identifier".equals(part.kind()))
            .findFirst()
            .map(NodeScopeNames::nameOf)
            .orElse(null);
    if (key != null) {
      walk.builder.declare(
          NodeFlowSyntax.text(key),
          BindingKind.TYPE,
          key.span(),
          Set.of(BindingFlag.TYPE_ONLY)
      );
    }
    node.children().forEach(this::types);
    walk.builder.close();
  }

  /** A signature inside a type declares its own type parameters, which its types may name. */
  private void signature(final SyntaxNode node) {
    walk.builder.open(ScopeKind.FUNCTION, node.span());
    typeParameters(NodeFlowSyntax.parts(node));
    node.children().forEach(this::types);
    walk.builder.close();
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
            Set.of(BindingFlag.TYPE_ONLY)
        );
      }
      parts.stream().skip(1).forEach(this::types);
    } else {
      node.children().forEach(this::declareTypeParameters);
    }
  }
}
