package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The types the project declares, grouped by module, and what each file can see of them. A name
 * resolves only when exactly one visible declaration has it, so an ambiguous name is unknown.
 */
final class SwiftTypeIndex {

  private final SwiftProjectLayout layout;
  private final Map<String, FileTypes> files;
  private final Map<String, List<SwiftTypeDecl>> declared;
  private final SwiftSupertypes supertypes = new SwiftSupertypes();

  /** A file's module, the modules it imports, its local type names and its nodes. */
  record FileTypes(
      String module,
      List<String> imports,
      List<String> localNames,
      List<SwiftTypeSyntax.Visit> visits) {}

  /** A type declaration with the syntax of the types it inherits from. */
  record Declaration(SwiftTypeDecl decl, List<SyntaxNode> inherits) {}

  /** An extension with the syntax of the type it extends and of the types it adds. */
  record Extension(String file, String scope, SyntaxNode extended, List<SyntaxNode> inherits) {}

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
    final List<Declaration> declarations = new ArrayList<>();
    final List<Extension> extensions = new ArrayList<>();
    for (final Map.Entry<String, SyntaxNode> tree : trees.entrySet()) {
      final String path = tree.getKey();
      final List<SwiftTypeSyntax.Visit> visits = SwiftTypeSyntax.visits(tree.getValue());
      final String module = layout.moduleOf(path);
      files.put(path, SwiftTypeDeclarations.fileTypes(module, visits));
      final SwiftTypeDeclarations.Found found = SwiftTypeDeclarations.collect(path, module, visits);
      declarations.addAll(found.declarations());
      extensions.addAll(found.extensions());
    }
    final SwiftTypeIndex index =
        new SwiftTypeIndex(layout, files, SwiftTypeDeclarations.byKey(declarations));
    index.supertypes.link(index, declarations, extensions);
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
        decl.kind(), decl.isAbstract(), decl.isFinal(), false, supertypes.of(decl));
  }

  /** Returns the name a type reference carries for {@code decl}: its module, then its name. */
  String refName(final SwiftTypeDecl decl) {
    return SwiftProjectLayout.IMPLICIT_MODULE.equals(decl.module())
        ? decl.qualifiedName()
        : decl.module() + SwiftTypeSyntax.NAME_SEPARATOR + decl.qualifiedName();
  }

  private Optional<SwiftTypeDecl> resolveHead(
      final FileTypes info, final String scope, final String first) {
    for (final String candidate : SwiftTypeSyntax.enclosingNames(scope, first)) {
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
        declared.getOrDefault(SwiftTypeDeclarations.key(module, qualified), List.of());
    return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
  }
}
