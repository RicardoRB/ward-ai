package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Helpers split out of {@link DeclarationFacts}. */
final class ShapeFacts {
  private static final String CLASS = "classDeclaration";

  private ShapeFacts() {}

  static Sym ofType(final TsType type) {
    return type instanceof TsType.Named named && named.ref() != null ? named.ref().sym() : null;
  }

  static TypeShape shapeOf(final Sym sym) {
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final TypeShape.Kind kind = kindOf(decl.kind());
      if (kind != null) {
        final boolean isAbstract = decl.has(Decl.ABSTRACT) || kind == TypeShape.Kind.INTERFACE;
        return new TypeShape(kind, isAbstract, false, false, supertypes(decl, located.scope()));
      }
    }
    return null;
  }

  static TypeShape.Kind kindOf(final Decl.Kind kind) {
    return switch (kind) {
      case CLASS -> TypeShape.Kind.CLASS;
      case INTERFACE -> TypeShape.Kind.INTERFACE;
      case ENUM -> TypeShape.Kind.ENUM;
      default -> null;
    };
  }

  static List<TypeRef> supertypes(final Decl decl, final Lookup scope) {
    final List<TypeRef> supertypes = new ArrayList<>();
    for (final TsType base : bases(decl, scope)) {
      if (base instanceof TsType.Named named && named.ref() != null && named.ref().sym() != null) {
        supertypes.add(TypeRefs.of(base));
      }
    }
    return supertypes;
  }

  static List<TsType> bases(final Decl decl, final Lookup scope) {
    final List<TsType> bases = new ArrayList<>();
    for (final TsType base : decl.header().extendsTypes()) {
      bases.add(Instantiate.apply(base, Map.of(), scope));
    }
    for (final TsType base : decl.header().implementsTypes()) {
      bases.add(Instantiate.apply(base, Map.of(), scope));
    }
    return bases;
  }
}
