package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the type syntax of a Swift file into type references. Project types resolve through the
 * index; the standard library names that every Swift program has are known by name. Any other name,
 * such as a Foundation or UIKit type the project does not declare, is unknown.
 */
final class SwiftTypeRefs {

  private static final Set<String> STANDARD_TYPES =
      Set.of(
          "Int",
          "Double",
          "Float",
          "String",
          "Bool",
          "Character",
          "Array",
          "Dictionary",
          "Set",
          "Optional");
  private static final String OPTIONAL = "Optional";
  private static final String ARRAY = "Array";
  private static final String OPTIONAL_MARK = "'?'";
  private static final String GENERIC_ARGUMENT = "generic_argument";
  private static final String GENERIC_CLAUSE = "generic_argument_clause";

  private final SwiftTypeIndex index;

  SwiftTypeRefs(final SwiftTypeIndex index) {
    this.index = index;
  }

  /** Returns the reference a type node spells at {@code scope}, or null when it is unknown. */
  TypeRef of(final SyntaxNode node, final String file, final String scope) {
    return switch (node.kind()) {
      case "type_identifier" -> identifier(node, file, scope);
      case "type_" -> wrapped(node, file, scope);
      case "array_type" -> element(node, file, scope);
      default -> null;
    };
  }

  /** Returns the project type a type identifier names, if the index resolves it. */
  Optional<SwiftTypeDecl> declOf(
      final SyntaxNode identifier, final String file, final String scope) {
    return index.resolve(file, scope, SwiftTypeSyntax.pathOf(identifier));
  }

  private TypeRef identifier(final SyntaxNode node, final String file, final String scope) {
    final List<String> path = SwiftTypeSyntax.pathOf(node);
    final List<TypeRef> arguments = arguments(node, path.size(), file, scope);
    if (arguments == null) {
      return null;
    }
    final Optional<SwiftTypeDecl> decl = index.resolve(file, scope, path);
    if (decl.isPresent()) {
      return new TypeRef(index.refName(decl.get()), arguments, 0, false);
    }
    final boolean standard = path.size() == 1 && STANDARD_TYPES.contains(path.get(0));
    return standard ? new TypeRef(path.get(0), arguments, 0, false) : null;
  }

  /** The generic arguments of a type identifier; null when one is unknown or a path has them. */
  private List<TypeRef> arguments(
      final SyntaxNode node, final int segments, final String file, final String scope) {
    final List<SyntaxNode> clauses = SwiftTypeSyntax.outermost(node, GENERIC_CLAUSE);
    if (clauses.isEmpty()) {
      return List.of();
    }
    if (segments != 1) {
      return null;
    }
    final List<TypeRef> arguments = new ArrayList<>();
    for (final SyntaxNode argument : SwiftTypeSyntax.outermost(clauses.get(0), GENERIC_ARGUMENT)) {
      final SyntaxNode type = SwiftTypeSyntax.typeChild(argument);
      final TypeRef ref = type == null ? null : of(type, file, scope);
      if (ref == null) {
        return null;
      }
      arguments.add(ref);
    }
    return arguments;
  }

  private TypeRef wrapped(final SyntaxNode node, final String file, final String scope) {
    final SyntaxNode inner = SwiftTypeSyntax.typeChild(node);
    final TypeRef type = inner == null ? null : of(inner, file, scope);
    if (type == null) {
      return null;
    }
    return SwiftTypeSyntax.hasChild(node, OPTIONAL_MARK)
        ? new TypeRef(OPTIONAL, List.of(type), 0, false)
        : type;
  }

  private TypeRef element(final SyntaxNode node, final String file, final String scope) {
    final SyntaxNode inner = SwiftTypeSyntax.typeChild(node);
    final TypeRef type = inner == null ? null : of(inner, file, scope);
    return type == null ? null : new TypeRef(ARRAY, List.of(type), 0, false);
  }
}
