package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The types the project declares, grouped by module, and what each file can see of them. A name
 * resolves only when exactly one visible declaration has it, so an ambiguous name is unknown.
 */
final class SwiftTypeIndex {

  private static final String KEY_SEPARATOR = "|";

  /** A file's module, the modules it imports, its local type names and its nodes. */
  private record FileTypes(
      String module,
      List<String> imports,
      Set<String> localNames,
      List<SwiftTypeSyntax.Visit> visits) {}

  /** A type declaration with the syntax of the types it inherits from. */
  private record Declaration(SwiftTypeDecl decl, List<SyntaxNode> inherits) {}

  /** An extension with the syntax of the type it extends and of the types it adds. */
  private record Extension(
      String file, String scope, SyntaxNode extended, List<SyntaxNode> inherits) {}

  private final SwiftProjectLayout layout;
  private final Map<String, FileTypes> files;
  private final Map<String, List<SwiftTypeDecl>> declared;
  private final Map<SwiftTypeDecl, List<TypeRef>> supertypes = new HashMap<>();

  private SwiftTypeIndex(
      final SwiftProjectLayout layout,
      final Map<String, FileTypes> files,
      final Map<String, List<SwiftTypeDecl>> declared) {
    this.layout = layout;
    this.files = files;
    this.declared = declared;
  }

  /** Indexes the parsed files, keyed by project-relative path. */
  static SwiftTypeIndex build(
      final SwiftProjectLayout layout, final Map<String, SyntaxNode> trees) {
    final Map<String, FileTypes> files = new HashMap<>();
    final Map<String, List<SwiftTypeDecl>> declared = new HashMap<>();
    final List<Declaration> declarations = new ArrayList<>();
    final List<Extension> extensions = new ArrayList<>();
    for (final Map.Entry<String, SyntaxNode> tree : trees.entrySet()) {
      final String path = tree.getKey();
      final List<SwiftTypeSyntax.Visit> visits = SwiftTypeSyntax.visits(tree.getValue());
      final String module = layout.moduleOf(path);
      files.put(path, fileTypes(module, visits));
      for (final SwiftTypeSyntax.Visit visit : visits) {
        if (SwiftTypeSyntax.isTypeDeclaration(visit.node().kind())
            && !visit.local()
            && !visit.inExtension()) {
          declare(path, module, visit, declarations, declared);
        }
        if (SwiftTypeSyntax.isExtension(visit.node().kind()) && !visit.local()) {
          extension(path, visit, extensions);
        }
      }
    }
    final SwiftTypeIndex index = new SwiftTypeIndex(layout, files, declared);
    index.linkSupertypes(declarations, extensions);
    return index;
  }

  /** Tells whether the index holds the file {@code path}. */
  boolean has(final String path) {
    return files.containsKey(path);
  }

  /** Returns the nodes of {@code path} with their scopes, or none for an unknown file. */
  List<SwiftTypeSyntax.Visit> visits(final String path) {
    final FileTypes file = files.get(path);
    return file == null ? List.of() : file.visits();
  }

  /** Returns the type that the dotted name {@code path} means at {@code scope} in {@code file}. */
  Optional<SwiftTypeDecl> resolve(final String file, final String scope, final List<String> path) {
    final FileTypes info = files.get(file);
    if (info == null || path.isEmpty() || info.localNames().contains(path.get(0))) {
      return Optional.empty();
    }
    Optional<SwiftTypeDecl> head = resolveHead(info, scope, path.get(0));
    for (final String segment : path.subList(1, path.size())) {
      head =
          head.flatMap(
              decl ->
                  unique(
                      decl.module(),
                      decl.qualifiedName() + SwiftTypeSyntax.NAME_SEPARATOR + segment));
    }
    return head;
  }

  /** Returns the type declared under {@code qualified} in the module of {@code file}. */
  Optional<SwiftTypeDecl> declaredAt(final String file, final String qualified) {
    final FileTypes info = files.get(file);
    return info == null
        ? Optional.empty()
        : unique(info.module(), qualified).filter(decl -> decl.file().equals(file));
  }

  /** Returns the shape of a declared type, with the supertypes that resolved. */
  TypeShape shapeOf(final SwiftTypeDecl decl) {
    return new TypeShape(
        decl.kind(),
        decl.isAbstract(),
        decl.isFinal(),
        false,
        supertypes.getOrDefault(decl, List.of()));
  }

  /** Returns the name a type reference carries for {@code decl}: its module, then its name. */
  String refName(final SwiftTypeDecl decl) {
    return SwiftProjectLayout.IMPLICIT_MODULE.equals(decl.module())
        ? decl.qualifiedName()
        : decl.module() + SwiftTypeSyntax.NAME_SEPARATOR + decl.qualifiedName();
  }

  private void linkSupertypes(
      final List<Declaration> declarations, final List<Extension> extensions) {
    for (final Declaration declaration : declarations) {
      final SwiftTypeDecl decl = declaration.decl();
      supertypes
          .computeIfAbsent(decl, key -> new ArrayList<>())
          .addAll(refsOf(decl.file(), decl.parentScope(), declaration.inherits()));
    }
    for (final Extension extension : extensions) {
      resolve(extension.file(), extension.scope(), SwiftTypeSyntax.pathOf(extension.extended()))
          .ifPresent(
              target ->
                  supertypes
                      .computeIfAbsent(target, key -> new ArrayList<>())
                      .addAll(refsOf(extension.file(), extension.scope(), extension.inherits())));
    }
  }

  private List<TypeRef> refsOf(
      final String file, final String scope, final List<SyntaxNode> types) {
    final List<TypeRef> refs = new ArrayList<>();
    for (final SyntaxNode type : types) {
      resolve(file, scope, SwiftTypeSyntax.pathOf(type))
          .ifPresent(decl -> refs.add(TypeRef.of(refName(decl))));
    }
    return refs;
  }

  private Optional<SwiftTypeDecl> resolveHead(
      final FileTypes info, final String scope, final String first) {
    for (final String candidate : enclosingNames(scope, first)) {
      final Optional<SwiftTypeDecl> found = unique(info.module(), candidate);
      if (found.isPresent()) {
        return found;
      }
    }
    final List<SwiftTypeDecl> imported = new ArrayList<>();
    for (final String module : info.imports()) {
      if (layout.declares(module)) {
        unique(module, first).ifPresent(imported::add);
      }
    }
    return imported.size() == 1 ? Optional.of(imported.get(0)) : Optional.empty();
  }

  private Optional<SwiftTypeDecl> unique(final String module, final String qualified) {
    final List<SwiftTypeDecl> found =
        declared.getOrDefault(module + KEY_SEPARATOR + qualified, List.of());
    return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
  }

  /** The candidate names of {@code first} from the innermost enclosing scope outwards. */
  private static List<String> enclosingNames(final String scope, final String first) {
    final List<String> names = new ArrayList<>();
    String current = scope;
    while (true) {
      names.add(SwiftTypeSyntax.qualify(current, first));
      if (current.isEmpty()) {
        return names;
      }
      final int dot = current.lastIndexOf(SwiftTypeSyntax.NAME_SEPARATOR);
      current = dot < 0 ? "" : current.substring(0, dot);
    }
  }

  private static FileTypes fileTypes(
      final String module, final List<SwiftTypeSyntax.Visit> visits) {
    final List<String> imports = new ArrayList<>();
    final Set<String> locals = new HashSet<>();
    for (final SwiftTypeSyntax.Visit visit : visits) {
      if (visit.local() && SwiftTypeSyntax.isTypeDeclaration(visit.node().kind())) {
        SwiftTypeSyntax.nameOf(visit.node()).ifPresent(locals::add);
      }
      if ("import_declaration".equals(visit.node().kind())) {
        importedModule(visit.node()).ifPresent(imports::add);
      }
    }
    return new FileTypes(module, imports, locals, visits);
  }

  private static Optional<String> importedModule(final SyntaxNode importDeclaration) {
    final SyntaxNode path = SwiftTypeSyntax.child(importDeclaration, "import_path");
    if (path == null) {
      return Optional.empty();
    }
    return path.children().stream()
        .filter(child -> "import_path_identifier".equals(child.kind()))
        .findFirst()
        .map(SwiftScopeWalk::text);
  }

  private static void declare(
      final String file,
      final String module,
      final SwiftTypeSyntax.Visit visit,
      final List<Declaration> declarations,
      final Map<String, List<SwiftTypeDecl>> declared) {
    final Optional<String> name = SwiftTypeSyntax.nameOf(visit.node());
    if (name.isEmpty()) {
      return;
    }
    final SwiftTypeDecl decl = declaration(file, module, visit.scope(), visit.node(), name.get());
    declarations.add(new Declaration(decl, SwiftTypeSyntax.inheritedTypes(visit.node())));
    declared
        .computeIfAbsent(module + KEY_SEPARATOR + decl.qualifiedName(), key -> new ArrayList<>())
        .add(decl);
  }

  private static SwiftTypeDecl declaration(
      final String file,
      final String module,
      final String scope,
      final SyntaxNode node,
      final String name) {
    final String qualified = SwiftTypeSyntax.qualify(scope, name);
    final boolean generic = SwiftTypeSyntax.hasChild(node, "generic_parameter_clause");
    return switch (node.kind()) {
      case "struct_declaration" ->
          new SwiftTypeDecl(
              module, file, qualified, scope, TypeShape.Kind.CLASS, true, false, generic);
      case "enum_declaration" ->
          new SwiftTypeDecl(
              module, file, qualified, scope, TypeShape.Kind.ENUM, true, false, generic);
      case "protocol_declaration" ->
          new SwiftTypeDecl(
              module, file, qualified, scope, TypeShape.Kind.INTERFACE, false, true, false);
      default ->
          new SwiftTypeDecl(
              module,
              file,
              qualified,
              scope,
              TypeShape.Kind.CLASS,
              SwiftTypeSyntax.hasChild(node, "'final'"),
              false,
              generic);
    };
  }

  private static void extension(
      final String file, final SwiftTypeSyntax.Visit visit, final List<Extension> extensions) {
    final SyntaxNode extended = SwiftTypeSyntax.child(visit.node(), "type_identifier");
    if (extended != null) {
      extensions.add(
          new Extension(
              file, visit.scope(), extended, SwiftTypeSyntax.inheritedTypes(visit.node())));
    }
  }
}
