package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a file is read through: its scopes, the parent of each node, its name tokens by position
 * and its name references by position.
 *
 * @param scopes the scopes of the file
 * @param parents the node that holds each node, by identity
 * @param names the identifier tokens by start offset
 * @param references the name references by start offset
 */
record NodeProgramIndex(
    UnitScopes scopes,
    Map<SyntaxNode,
    SyntaxNode> parents,
    Map<Integer,
    SyntaxNode> names,
    Map<Integer,
    NameReference> references
) {
  private static final String IDENTIFIER = "Identifier";

  /** Builds the index of a file. */
  static NodeProgramIndex of(final SourceUnit unit, final SyntaxNode root) {
    final UnitScopes scopes = new NodeScopeAnalysis(environments -> Set.of()).analyze(unit, root);
    final Map<SyntaxNode, SyntaxNode> parents = new IdentityHashMap<>();
    final Map<Integer, SyntaxNode> names = new HashMap<>();
    final Map<Integer, NameReference> references = new HashMap<>();
    scopes.references()
        .forEach(reference -> references.putIfAbsent(reference.span().startOffset(), reference));
    walk(root, parents, names);
    return new NodeProgramIndex(scopes, parents, names, references);
  }

  private static void walk(
      final SyntaxNode node,
      final Map<SyntaxNode,
      SyntaxNode> parents,
      final Map<Integer,
      SyntaxNode> names
  ) {
    if (IDENTIFIER.equals(node.kind())) {
      names.put(node.span().startOffset(), node);
    }
    for (final SyntaxNode child : node.children()) {
      parents.put(child, node);
      walk(child, parents, names);
    }
  }
}
