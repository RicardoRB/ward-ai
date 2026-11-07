package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The supertypes of each declared type: its own inheritance clause plus the extensions of it. */
final class SwiftSupertypes {

  private final Map<SwiftTypeDecl, List<TypeRef>> byDecl = new HashMap<>();

  /** Returns the supertypes that resolved for {@code decl}, empty when none did. */
  List<TypeRef> of(final SwiftTypeDecl decl) {
    return byDecl.getOrDefault(decl, List.of());
  }

  /** Links every declaration's inheritance and every extension's conformances. */
  void link(
      final SwiftTypeIndex index,
      final List<SwiftTypeIndex.Declaration> declarations,
      final List<SwiftTypeIndex.Extension> extensions) {
    for (final SwiftTypeIndex.Declaration declaration : declarations) {
      final SwiftTypeDecl decl = declaration.decl();
      add(decl, refsOf(index, decl.file(), decl.parentScope(), declaration.inherits()));
    }
    for (final SwiftTypeIndex.Extension extension : extensions) {
      index
          .resolve(extension.file(), extension.scope(), SwiftNodes.pathOf(extension.extended()))
          .ifPresent(
              target ->
                  add(
                      target,
                      refsOf(index, extension.file(), extension.scope(), extension.inherits())));
    }
  }

  private void add(final SwiftTypeDecl decl, final List<TypeRef> refs) {
    byDecl.computeIfAbsent(decl, key -> new ArrayList<>()).addAll(refs);
  }

  private static List<TypeRef> refsOf(
      final SwiftTypeIndex index,
      final String file,
      final String scope,
      final List<SyntaxNode> types) {
    final List<TypeRef> refs = new ArrayList<>();
    for (final SyntaxNode type : types) {
      index
          .resolve(file, scope, SwiftNodes.pathOf(type))
          .ifPresent(decl -> refs.add(TypeRef.of(index.refName(decl))));
    }
    return refs;
  }
}
