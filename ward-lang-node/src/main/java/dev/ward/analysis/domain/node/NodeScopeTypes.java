package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles classes, TypeScript type declarations and exports. */
final class NodeScopeTypes {

  private static final String IDENTIFIER = "identifier";

  private final NodeScopeWalk walk;

  NodeScopeTypes(final NodeScopeWalk walk) {
    this.walk = walk;
  }

  void classDeclaration(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.LEXICAL);
    if (walk.exporting && walk.builder.current() == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    if (name != null && "classDeclaration".equals(node.kind())) {
      walk.builder.declare(NodeFlowSyntax.text(name), BindingKind.CLASS, name.span(), flags);
    }
    walk.builder.open(ScopeKind.CLASS, node.span());
    parts.stream().filter(part -> !part.equals(name)).forEach(walk::visit);
    walk.builder.close();
  }

  void typeDeclaration(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    final boolean typeOnly = !"enumDeclaration".equals(node.kind());
    final Set<BindingFlag> flags =
        typeOnly ? EnumSet.of(BindingFlag.TYPE_ONLY) : EnumSet.noneOf(BindingFlag.class);
    if (walk.exporting && walk.builder.current() == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    if (name != null) {
      walk.builder.declare(NodeFlowSyntax.text(name), BindingKind.TYPE, name.span(), flags);
    }
    walk.builder.open(ScopeKind.BLOCK, node.span());
    walk.expressions.typeRefs.typeParameters(parts);
    parts.stream()
        .filter(part -> !part.equals(name) && !"typeParameters".equals(part.kind()))
        .forEach(walk::visit);
    walk.builder.close();
  }

  void namespaceDeclaration(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, "namespaceName");
    if (name != null && NodeScopeNames.nameOf(name) != null) {
      final SyntaxNode leaf = NodeScopeNames.nameOf(name);
      walk.builder.declare(NodeFlowSyntax.text(leaf), BindingKind.TYPE, leaf.span(), Set.of());
    }
    walk.builder.open(ScopeKind.BLOCK, node.span());
    parts.stream().filter(part -> !part.equals(name)).forEach(walk::visit);
    walk.builder.close();
  }

  void exportStatement(final SyntaxNode node) {
    final boolean outer = walk.exporting;
    walk.exporting = true;
    walk.generic(node);
    walk.exporting = outer;
  }

  void exportFromBlock(final SyntaxNode node) {
    final boolean reexport =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), "importFrom") != null
            || containsFrom(node);
    if (!reexport) {
      walk.generic(node);
    }
  }

  static boolean containsFrom(final SyntaxNode node) {
    return !NodeFlowSyntax.allOf(NodeFlowSyntax.parts(node), "importFrom").isEmpty()
        || NodeFlowSyntax.parts(node).stream().anyMatch(part -> "importFrom".equals(part.kind()));
  }
}
