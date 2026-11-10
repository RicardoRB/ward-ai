package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles the imports, types, functions and declarations of Swift. */
final class SwiftScopeTypes {

  private static final Set<String> NAMED_TYPES =
      Set.of("struct_name", "class_name", "enum_name", "protocol_name", "actor_name");

  private final SwiftScopeWalk walk;

  SwiftScopeTypes(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  void importDeclaration(final SyntaxNode node) {
    final SyntaxNode path = SwiftScopeWalk.child(node, "import_path");
    final List<SyntaxNode> segments =
        path.children().stream().filter(c -> "import_path_identifier".equals(c.kind())).toList();
    final boolean member = SwiftScopeWalk.child(node, "import_kind") != null;
    final SyntaxNode bound = member ? segments.get(segments.size() - 1) : segments.get(0);
    walk.builder.declareImport(
        SwiftScopeWalk.text(bound),
        bound.span(),
        new ImportOrigin(
            SwiftScopeWalk.text(path),
            SwiftScopeWalk.text(segments.get(segments.size() - 1)),
            node.span()),
        EnumSet.noneOf(BindingFlag.class));
  }

  void typeLike(final SyntaxNode node) {
    final SyntaxNode name =
        node.children().stream()
            .filter(c -> NAMED_TYPES.contains(c.kind()))
            .findFirst()
            .orElse(null);
    if (name != null) {
      walk.declare(name, BindingKind.CLASS, false, false);
    }
    final boolean inherits =
        SwiftScopeWalk.child(node, "type_inheritance_clause") != null
            || "extension_declaration".equals(node.kind())
            || "protocol_declaration".equals(node.kind());
    walk.scoped(node, ScopeKind.CLASS, inherits);
  }

  void function(final SyntaxNode node) {
    final SyntaxNode name = SwiftScopeWalk.child(node, "function_name");
    if (name != null) {
      walk.builder.declare(SwiftScopeWalk.text(name), BindingKind.FUNCTION, name.span(), Set.of());
    }
    walk.scoped(node, ScopeKind.FUNCTION, false);
  }

  void parameter(final SyntaxNode node) {
    final SyntaxNode local = SwiftScopeWalk.child(node, "local_parameter_name");
    final SyntaxNode name =
        local == null ? SwiftScopeWalk.child(node, "external_parameter_name") : local;
    if (name != null && !"_".equals(SwiftScopeWalk.text(name))) {
      walk.declare(name, BindingKind.PARAMETER, false, false);
    }
    node.children().stream()
        .filter(c -> "default_argument_clause".equals(c.kind()))
        .forEach(walk::visit);
  }

  void typeAlias(final SyntaxNode node) {
    final SyntaxNode name = SwiftScopeWalk.child(node, "typealias_name");
    if (name != null) {
      walk.builder.declare(
          SwiftScopeWalk.text(name),
          BindingKind.TYPE,
          name.span(),
          EnumSet.of(BindingFlag.TYPE_ONLY));
    }
  }

  void enumCase(final SyntaxNode node) {
    walk.declare(SwiftScopeWalk.child(node, "enum_case_name"), BindingKind.CONSTANT, false, false);
  }

  void declaration(final SyntaxNode node, final BindingKind kind) {
    final BindingKind outer = walk.declaring;
    walk.declaring = kind;
    final SyntaxNode name = SwiftScopeWalk.child(node, "variable_name");
    if (name == null) {
      walk.generic(node);
    } else {
      walk.declare(name, kind, false, false);
      node.children().stream().filter(c -> !c.equals(name)).forEach(walk.closures::bodyOrAccessors);
    }
    walk.declaring = outer;
  }
}
