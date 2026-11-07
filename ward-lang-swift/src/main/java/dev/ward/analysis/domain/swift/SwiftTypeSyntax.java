package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks a Swift syntax tree once and records, for every node, the type scope it sits in. Type
 * declarations in function or closure bodies and inside extensions are marked, because the type
 * rules do not declare or resolve them.
 */
final class SwiftTypeSyntax {

  /** The separator of nested type names, as in {@code Outer.Inner}. */
  static final String NAME_SEPARATOR = ".";

  private static final String TYPE_IDENTIFIER = SwiftNodes.TYPE_IDENTIFIER;
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

  /** The context a node inherits from its ancestors while the walk descends. */
  private record Context(String scope, String parentKind, boolean local, boolean inExtension) {}

  /** Returns every node of {@code root} with its scope, in document order. */
  static List<Visit> visits(final SyntaxNode root) {
    final List<Visit> visits = new ArrayList<>();
    collect(root, new Context("", "", false, false), visits);
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

  /** Returns {@code name} qualified by the enclosing type scope. */
  static String qualify(final String scope, final String name) {
    return scope.isEmpty() ? name : scope + NAME_SEPARATOR + name;
  }

  /** Tells whether {@code visit} is a type identifier that continues a dotted type name. */
  static boolean isChained(final Visit visit) {
    return TYPE_IDENTIFIER.equals(visit.parentKind());
  }

  /** The candidate names of {@code first} from the innermost enclosing scope outwards. */
  static List<String> enclosingNames(final String scope, final String first) {
    final List<String> names = new ArrayList<>();
    String current = scope;
    while (true) {
      names.add(qualify(current, first));
      if (current.isEmpty()) {
        return names;
      }
      final int dot = current.lastIndexOf(NAME_SEPARATOR);
      current = dot < 0 ? "" : current.substring(0, dot);
    }
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

  private static void collect(
      final SyntaxNode node, final Context context, final List<Visit> visits) {
    visits.add(
        new Visit(
            node, context.scope(), context.parentKind(), context.local(), context.inExtension()));
    final String inner =
        isTypeDeclaration(node.kind())
            ? nameOf(node).map(name -> qualify(context.scope(), name)).orElse(context.scope())
            : context.scope();
    final Context nested =
        new Context(
            inner,
            node.kind(),
            context.local() || LOCAL_BODIES.contains(node.kind()),
            context.inExtension() || EXTENSION_BODY.equals(node.kind()));
    for (final SyntaxNode child : node.children()) {
      collect(child, nested, visits);
    }
  }
}
