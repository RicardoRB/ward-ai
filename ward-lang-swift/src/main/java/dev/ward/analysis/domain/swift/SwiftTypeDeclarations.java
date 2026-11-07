package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Reads the type declarations, extensions and imports one Swift file contributes to the index. */
final class SwiftTypeDeclarations {

  private static final String KEY_SEPARATOR = "|";
  private static final String IMPORT = "import_declaration";
  private static final String IMPORT_PATH = "import_path";
  private static final String IMPORT_IDENTIFIER = "import_path_identifier";
  private static final String GENERIC_PARAMETERS = "generic_parameter_clause";
  private static final String FINAL = "'final'";
  private static final String STRUCT = "struct_declaration";
  private static final String ENUM = "enum_declaration";
  private static final String PROTOCOL = "protocol_declaration";

  /** The declarations and extensions that one file contributes. */
  record Found(
      List<SwiftTypeIndex.Declaration> declarations, List<SwiftTypeIndex.Extension> extensions) {}

  private SwiftTypeDeclarations() {}

  /** Returns the key of a module's type with the qualified name {@code qualified}. */
  static String key(final String module, final String qualified) {
    return module + KEY_SEPARATOR + qualified;
  }

  /** Groups the declared types by {@link #key}, so each name lists the types that declare it. */
  static Map<String, List<SwiftTypeDecl>> byKey(
      final List<SwiftTypeIndex.Declaration> declarations) {
    return declarations.stream()
        .map(SwiftTypeIndex.Declaration::decl)
        .collect(
            Collectors.groupingBy(
                decl -> key(decl.module(), decl.qualifiedName()),
                HashMap::new,
                Collectors.toList()));
  }

  /** Returns the module, the imports and the local type names of a file. */
  static SwiftTypeIndex.FileTypes fileTypes(
      final String module, final List<SwiftTypeSyntax.Visit> visits) {
    final List<String> imports = new ArrayList<>();
    final List<String> locals = new ArrayList<>();
    for (final SwiftTypeSyntax.Visit visit : visits) {
      final SyntaxNode node = visit.node();
      if (visit.local() && SwiftTypeSyntax.isTypeDeclaration(node.kind())) {
        SwiftTypeSyntax.nameOf(node).ifPresent(locals::add);
      }
      if (IMPORT.equals(node.kind())) {
        importedModule(node).ifPresent(imports::add);
      }
    }
    return new SwiftTypeIndex.FileTypes(module, imports, List.copyOf(locals), visits);
  }

  /** Returns the types and extensions declared at the level of file {@code file}. */
  static Found collect(
      final String file, final String module, final List<SwiftTypeSyntax.Visit> visits) {
    final List<SwiftTypeIndex.Declaration> declarations = new ArrayList<>();
    final List<SwiftTypeIndex.Extension> extensions = new ArrayList<>();
    for (final SwiftTypeSyntax.Visit visit : visits) {
      if (SwiftTypeSyntax.isTypeDeclaration(visit.node().kind())
          && !visit.local()
          && !visit.inExtension()) {
        declaration(file, module, visit).ifPresent(declarations::add);
      }
      if (SwiftTypeSyntax.isExtension(visit.node().kind()) && !visit.local()) {
        extension(file, visit).ifPresent(extensions::add);
      }
    }
    return new Found(declarations, extensions);
  }

  private static Optional<SwiftTypeIndex.Declaration> declaration(
      final String file, final String module, final SwiftTypeSyntax.Visit visit) {
    return SwiftTypeSyntax.nameOf(visit.node())
        .map(
            name ->
                new SwiftTypeIndex.Declaration(
                    declaredType(file, module, visit.scope(), visit.node(), name),
                    SwiftNodes.inheritedTypes(visit.node())));
  }

  private static SwiftTypeDecl declaredType(
      final String file,
      final String module,
      final String scope,
      final SyntaxNode node,
      final String name) {
    final String qualified = SwiftTypeSyntax.qualify(scope, name);
    final boolean generic = SwiftNodes.hasChild(node, GENERIC_PARAMETERS);
    return switch (node.kind()) {
      case STRUCT ->
          new SwiftTypeDecl(
              module, file, qualified, scope, TypeShape.Kind.CLASS, true, false, generic);
      case ENUM ->
          new SwiftTypeDecl(
              module, file, qualified, scope, TypeShape.Kind.ENUM, true, false, generic);
      case PROTOCOL ->
          new SwiftTypeDecl(
              module, file, qualified, scope, TypeShape.Kind.INTERFACE, false, true, false);
      default ->
          new SwiftTypeDecl(
              module,
              file,
              qualified,
              scope,
              TypeShape.Kind.CLASS,
              SwiftNodes.hasChild(node, FINAL),
              false,
              generic);
    };
  }

  private static Optional<SwiftTypeIndex.Extension> extension(
      final String file, final SwiftTypeSyntax.Visit visit) {
    final SyntaxNode extended = SwiftNodes.child(visit.node(), SwiftNodes.TYPE_IDENTIFIER);
    return extended == null
        ? Optional.empty()
        : Optional.of(
            new SwiftTypeIndex.Extension(
                file, visit.scope(), extended, SwiftNodes.inheritedTypes(visit.node())));
  }

  private static Optional<String> importedModule(final SyntaxNode importDeclaration) {
    final SyntaxNode path = SwiftNodes.child(importDeclaration, IMPORT_PATH);
    if (path == null) {
      return Optional.empty();
    }
    return path.children().stream()
        .filter(child -> IMPORT_IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SwiftScopeWalk::text);
  }
}
