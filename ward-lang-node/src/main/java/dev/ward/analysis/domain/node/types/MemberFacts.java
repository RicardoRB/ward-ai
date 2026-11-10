package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The methods and fields of a class, interface, enum or object type, declared and inherited, so a
 * rule can tell a method from a property. A type whose declaration the resolver does not know has
 * no members here.
 */
final class MemberFacts {

  private static final int MAX_DEPTH = 8;
  private static final Set<Decl.Kind> DECLARED_KINDS =
      Set.of(Decl.Kind.CLASS, Decl.Kind.INTERFACE, Decl.Kind.ENUM);

  private MemberFacts() {}

  /** The members a declaration has itself, before those it inherits. */
  private record Own(List<CallTarget> methods, List<FieldRef> fields) {}

  /** Returns the members of {@code type}, or null when its declaration is unknown. */
  static TypeMembers of(final TsType type) {
    final Own self = declared(type);
    if (self == null) {
      return null;
    }
    final List<TypeRef> ancestors = new ArrayList<>();
    final List<CallTarget> methods = new ArrayList<>();
    final List<FieldRef> fields = new ArrayList<>();
    for (final TsType base : bases(type)) {
      final Own inherited = flattened(base, 1);
      if (inherited == null) {
        return null;
      }
      ancestors.add(TypeRefs.of(base));
      methods.addAll(inherited.methods());
      fields.addAll(inherited.fields());
    }
    return new TypeMembers(
        self.methods(), self.fields(), ancestors, List.of(), methods, fields, Map.of(), List.of());
  }

  /** The members of {@code type} with those of every supertype above it, or null if unknown. */
  private static Own flattened(final TsType type, final int depth) {
    final Own own = depth > MAX_DEPTH ? null : declared(type);
    if (own == null) {
      return null;
    }
    final List<CallTarget> methods = new ArrayList<>(own.methods());
    final List<FieldRef> fields = new ArrayList<>(own.fields());
    for (final TsType base : bases(type)) {
      final Own inherited = flattened(base, depth + 1);
      if (inherited == null) {
        return null;
      }
      methods.addAll(inherited.methods());
      fields.addAll(inherited.fields());
    }
    return new Own(methods, fields);
  }

  /** The members a type declares itself, or null when the type is not a known declaration. */
  private static Own declared(final TsType type) {
    if (type instanceof TsType.Obj obj) {
      final TypeRef self = TypeRefs.of(type);
      return self == null ? null : own(obj.members(), self.name());
    }
    final Optional<Sym.Located> found = declarationOf(type);
    return found.map(located -> own(located.decl().members(), located.decl().name())).orElse(null);
  }

  private static Own own(final List<Member> members, final String owner) {
    final List<CallTarget> methods = new ArrayList<>();
    final List<FieldRef> fields = new ArrayList<>();
    for (final Member member : members) {
      if (member.isMethod()) {
        methods.add(
            TypeRefs.target(owner, member.name(), member.sigs().get(0), member.has(Member.STATIC)));
      } else {
        fields.add(
            new FieldRef(
                member.name(), TypeRefs.of(member.type()), member.has(Member.STATIC), List.of()));
      }
    }
    return new Own(methods, fields);
  }

  /** The base types a declaration extends or implements, each resolved in its own scope. */
  private static List<TsType> bases(final TsType type) {
    final List<TsType> bases = new ArrayList<>();
    declarationOf(type)
        .ifPresent(
            located -> {
              final Decl decl = located.decl();
              decl.header().extendsTypes().forEach(base -> bases.add(resolved(base, located)));
              decl.header().implementsTypes().forEach(base -> bases.add(resolved(base, located)));
            });
    return bases;
  }

  private static TsType resolved(final TsType base, final Sym.Located located) {
    return Instantiate.apply(base, Map.of(), located.scope());
  }

  /** The class, interface or enum declaration a named type refers to, with its scope. */
  private static Optional<Sym.Located> declarationOf(final TsType type) {
    if (!(type instanceof TsType.Named named) || named.ref() == null || named.ref().sym() == null) {
      return Optional.empty();
    }
    return named.ref().sym().types().stream()
        .filter(located -> DECLARED_KINDS.contains(located.decl().kind()))
        .findFirst();
  }
}
