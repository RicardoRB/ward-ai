package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the syntax the native Swift type resolver needs: the walk that records each node's type
 * scope, declaration names, dotted type paths and inheritance clauses.
 */
final class SwiftTypeSyntax {

  /** The separator of nested type names, as in {@code Outer.Inner}. */
  static final String NAME_SEPARATOR = ".";

  private static final String TYPE_IDENTIFIER = "type_identifier";
  private static final String TYPE_WRAPPER = "type_";
  private static final String ARRAY_TYPE = "array_type";
  private static final String INHERITANCE_CLAUSE = "type_inheritance_clause";
  private static final String EXTENSION = "extension_declaration";
  private static final String EXTENSION_BODY = "extension_body";
  private static final String BODY_SUFFIX = "_body";
  private static final Set<String> TYPE_DECLARATIONS =
      Set.of("class_declaration", "struct_declaration", "enum_declaration", "protocol_declaration");
  private static final Set<String> NAME_KINDS =
      Set.of("class_name", "struct_name", "enum_name", "protocol_name");
  private static final Set<String> LOCAL_BODIES =
      Set.of("code_block", "function_declaration", "closure_expression");

  private SwiftTypeSyntax() {}

  /**
   * A node with the context a type rule needs: the qualified type scope it sits in, its parent's
   * kind, and whether it lies in a function body or an extension body.
   *
   * @param node the syntax node
   * @param scope the qualified name of the enclosing type declaration, empty at file level
   * @param parentKind the kind of the parent node, empty for the root
   * @param local whether a function, closure or code block encloses it
   * @param inExtension whether an extension body encloses it
   */
  record Visit(
      SyntaxNode node, String scope, String parentKind, boolean local, boolean inExtension) {}

  /** Returns every node of {@code root} with its scope, in document order. */
  static List<Visit> visits(final SyntaxNode root) {
    final List<Visit> visits = new ArrayList<>();
    collect(root, "", "", false, false, visits);
    return visits;
  }

  /** Tells whether {@code kind} is a class, struct, enum or protocol declaration. */
  static boolean isTypeDeclaration(final String kind) {
    return TYPE_DECLARATIONS.contains(kind);
  }

  /** Tells whether {@code kind} is an extension declaration. */
  static boolean isExtension(final String kind) {
    return EXTENSION.equals(kind);
  }

  /** Returns the name a type declaration declares. */
  static Optional<String> nameOf(final SyntaxNode declaration) {
    return findName(declaration).map(SwiftScopeWalk::text);
  }

  /** Finds the name node before any body, where an enum keeps its name under a wrapper. */
  private static Optional<SyntaxNode> findName(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (NAME_KINDS.contains(child.kind())) {
        return Optional.of(child);
      }
      final Optional<SyntaxNode> nested =
          child.kind().endsWith(BODY_SUFFIX) ? Optional.empty() : findName(child);
      if (nested.isPresent()) {
        return nested;
      }
    }
    return Optional.empty();
  }

  /** Returns {@code name} qualified by the enclosing type scope. */
  static String qualify(final String scope, final String name) {
    return scope.isEmpty() ? name : scope + NAME_SEPARATOR + name;
  }

  /** Returns the dotted name segments of a type identifier, without generic arguments. */
  static List<String> pathOf(final SyntaxNode identifier) {
    final List<String> path = new ArrayList<>();
    SyntaxNode current = identifier;
    while (current != null) {
      final SyntaxNode name = child(current, "type_name");
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
        .filter(
            child ->
                TYPE_IDENTIFIER.equals(child.kind())
                    || TYPE_WRAPPER.equals(child.kind())
                    || ARRAY_TYPE.equals(child.kind()))
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

  /** Tells whether {@code visit} is a type identifier that continues a dotted type name. */
  static boolean isChained(final Visit visit) {
    return TYPE_IDENTIFIER.equals(visit.parentKind());
  }

  private static void collect(
      final SyntaxNode node,
      final String scope,
      final String parentKind,
      final boolean local,
      final boolean inExtension,
      final List<Visit> visits) {
    visits.add(new Visit(node, scope, parentKind, local, inExtension));
    final String inner =
        isTypeDeclaration(node.kind())
            ? nameOf(node).map(name -> qualify(scope, name)).orElse(scope)
            : scope;
    final boolean nestedLocal = local || LOCAL_BODIES.contains(node.kind());
    final boolean nestedExtension = inExtension || EXTENSION_BODY.equals(node.kind());
    for (final SyntaxNode child : node.children()) {
      collect(child, inner, node.kind(), nestedLocal, nestedExtension, visits);
    }
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
