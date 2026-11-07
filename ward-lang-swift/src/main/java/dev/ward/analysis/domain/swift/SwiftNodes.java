package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Navigates the Swift syntax tree for type names, type syntax and inheritance clauses. */
final class SwiftNodes {

  /** The kind of a type identifier, which may continue a dotted name. */
  static final String TYPE_IDENTIFIER = "type_identifier";

  private static final String TYPE_NAME = "type_name";
  private static final String TYPE_WRAPPER = "type_";
  private static final String ARRAY_TYPE = "array_type";
  private static final String INHERITANCE_CLAUSE = "type_inheritance_clause";
  private static final Set<String> TYPE_KINDS = Set.of(TYPE_IDENTIFIER, TYPE_WRAPPER, ARRAY_TYPE);

  private SwiftNodes() {}

  /** Returns the dotted name segments of a type identifier, without generic arguments. */
  static List<String> pathOf(final SyntaxNode identifier) {
    final List<String> path = new ArrayList<>();
    SyntaxNode current = identifier;
    while (current != null) {
      final SyntaxNode name = child(current, TYPE_NAME);
      if (name == null) {
        return List.of();
      }
      path.add(SwiftScopeWalk.text(name));
      current = child(current, TYPE_IDENTIFIER);
    }
    return path;
  }

  /** Returns the type identifiers named by the inheritance clauses directly under {@code node}. */
  static List<SyntaxNode> inheritedTypes(final SyntaxNode node) {
    final List<SyntaxNode> types = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (INHERITANCE_CLAUSE.equals(child.kind())) {
        collectIdentifiers(child, types);
      }
    }
    return types;
  }

  /** Returns the first child of {@code node} that is a type, a type wrapper or an array type. */
  static SyntaxNode typeChild(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> TYPE_KINDS.contains(child.kind()))
        .findFirst()
        .orElse(null);
  }

  /** Returns the first direct child of {@code node} with {@code kind}, or null. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream()
        .filter(child -> kind.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }

  /** Tells whether {@code node} has a direct child of {@code kind}. */
  static boolean hasChild(final SyntaxNode node, final String kind) {
    return child(node, kind) != null;
  }

  /** Returns the outermost descendants of {@code node} with {@code kind}, in order. */
  static List<SyntaxNode> outermost(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        found.add(child);
      } else {
        found.addAll(outermost(child, kind));
      }
    }
    return found;
  }

  private static void collectIdentifiers(final SyntaxNode node, final List<SyntaxNode> types) {
    for (final SyntaxNode child : node.children()) {
      if (TYPE_IDENTIFIER.equals(child.kind())) {
        types.add(child);
      } else {
        collectIdentifiers(child, types);
      }
    }
  }
}
