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
import java.util.Set;

/** A parsed unit with its scopes, its declarations and an index of its nodes by span. */
final class UnitModel {
  private static final Set<String> PARAMETER_KINDS =
      Set.of(
          "requiredParameter",
          "optionalParameter",
          "restParameter",
          "formalParameterArg",
          "lastFormalParameterArg");

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

  private final SourceUnit unit;
  private final SyntaxNode tree;
  private final UnitScopes scopes;
  private final Module module;
  private final Map<String, NameReference> references = new HashMap<>();
  private final Map<String, Site> sites = new HashMap<>();
  private final Map<String, List<SyntaxNode>> nodes = new HashMap<>();
  private final Map<SyntaxNode, SyntaxNode> parents = new IdentityHashMap<>();

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
    declare(node);
    for (final SyntaxNode child : node.children()) {
      index(child, node);
    }
  }

  private static boolean declaresConst(final SyntaxNode node) {
    final SyntaxNode modifier = node == null ? null : TreeKit.child(node, "varModifier");
    return modifier != null && "const".equals(TreeKit.text(modifier));
  }

  private void declare(final SyntaxNode node) {
    switch (node.kind()) {
      case "variableDeclaration" -> {
        final SyntaxNode list = parents.get(node);
        final boolean isConst =
            declaresConst(list) || list != null && declaresConst(parents.get(list));
        site(TreeKit.variableName(node), SiteKind.VARIABLE, node, isConst);
      }
      case "functionDeclaration" ->
          site(TreeKit.child(node, "identifier"), SiteKind.FUNCTION, node, false);
      case "classDeclaration" ->
          site(TreeKit.child(node, "identifier"), SiteKind.CLASS, node, false);
      case "arrowFunctionParameters" -> {
        final SyntaxNode single = TreeKit.child(node, "propertyName");
        if (single != null) {
          site(single, SiteKind.PARAMETER, node, false);
        }
      }
      default -> {
        if (PARAMETER_KINDS.contains(node.kind())) {
          site(TreeKit.find(node, "identifier"), SiteKind.PARAMETER, node, false);
        }
      }
    }
  }

  private void site(
      final SyntaxNode name, final SiteKind kind, final SyntaxNode node, final boolean isConst) {
    final SyntaxNode identifier =
        name == null
            ? null
            : "identifier".equals(name.kind()) ? name : TreeKit.find(name, "identifier");
    if (identifier != null) {
      sites.put(UnitTypeFacts.key(identifier.span()), new Site(kind, node, isConst));
    }
  }
}
