package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A parsed unit with its scopes, its declarations and an index of its nodes by span. */
final class UnitModel {

  private final SourceUnit unit;
  private final SyntaxNode tree;
  private final UnitScopes scopes;
  private final Module module;
  private final Map<String, NameReference> references = new HashMap<>();
  private final Map<String, Site> sites = new HashMap<>();
  private final Map<String, List<SyntaxNode>> nodes = new HashMap<>();
  private final Map<SyntaxNode, SyntaxNode> parents = new IdentityHashMap<>();

  /** What a name is declared by. */
  enum SiteKind {
    /** A variable declaration. */
    VARIABLE,
    /** A parameter. */
    PARAMETER,
    /** A function declaration. */
    FUNCTION,
    /** A class declaration. */
    CLASS
  }

  /**
   * The declaration of a name.
   *
   * @param kind what declares it
   * @param node the declaring node
   * @param isConst whether a variable is declared with {@code const}
   */
  record Site(SiteKind kind, SyntaxNode node, boolean isConst) {}

  UnitModel(
      final SourceUnit unit, final SyntaxNode tree, final UnitScopes scopes, final Module module) {
    this.unit = unit;
    this.tree = tree;
    this.scopes = scopes;
    this.module = module;
    for (final NameReference reference : scopes.references()) {
      references.put(UnitTypeFacts.key(reference.span()), reference);
    }
    index(tree, null);
  }

  SourceUnit unit() {
    return unit;
  }

  SyntaxNode tree() {
    return tree;
  }

  UnitScopes scopes() {
    return scopes;
  }

  Module module() {
    return module;
  }

  /** Returns the name reference at {@code span}, or null. */
  NameReference reference(final Span span) {
    return references.get(UnitTypeFacts.key(span));
  }

  /** Returns the site that declares the name at {@code span}, or null. */
  Site site(final Span span) {
    return sites.get(UnitTypeFacts.key(span));
  }

  /** Returns the nodes with exactly {@code span}, outermost first. */
  List<SyntaxNode> nodesAt(final Span span) {
    return nodes.getOrDefault(UnitTypeFacts.key(span), List.of());
  }

  /** Returns the parent of {@code node}, or null for the root. */
  SyntaxNode parent(final SyntaxNode node) {
    return parents.get(node);
  }

  private void index(final SyntaxNode node, final SyntaxNode parent) {
    if (parent != null) {
      parents.put(node, parent);
    }
    nodes.computeIfAbsent(UnitTypeFacts.key(node.span()), key -> new ArrayList<>()).add(node);
    SiteDeclarer.declare(node, parents, sites);
    for (final SyntaxNode child : node.children()) {
      index(child, node);
    }
  }
}
