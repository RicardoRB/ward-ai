package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Substitutes type parameters in a type and binds the remaining names to the scope that declared
 * them, so the type can be read from anywhere.
 */
final class Inst {
  private Inst() {}

  /**
   * Returns {@code type} with the names in {@code subst} replaced and the others bound to {@code
   * scope}; a null scope leaves them unbound.
   */
  static Ty apply(final Ty type, final Map<String, Ty> subst, final Lookup scope) {
    return switch (type) {
      case Ty.Named named -> named(named, subst, scope);
      case Ty.Union union -> new Ty.Union(all(union.parts(), subst, scope));
      case Ty.Inter inter -> new Ty.Inter(all(inter.parts(), subst, scope));
      case Ty.Tuple tuple -> new Ty.Tuple(all(tuple.elements(), subst, scope));
      case Ty.Arr arr -> new Ty.Arr(apply(arr.element(), subst, scope));
      case Ty.Fn fn -> new Ty.Fn(sig(fn.sig(), subst, scope));
      case Ty.Obj obj -> new Ty.Obj(members(obj.members(), subst, scope));
      case Ty.Typeof typeOf -> typeOf;
      case Ty.Unknown unknown -> unknown;
    };
  }

  private static Ty named(final Ty.Named named, final Map<String, Ty> subst, final Lookup scope) {
    if (named.args().isEmpty() && subst.containsKey(named.name())) {
      return subst.get(named.name());
    }
    final List<Ty> args = all(named.args(), subst, scope);
    final Ref ref =
        named.ref() != null || scope == null || !isReference(named.name())
            ? named.ref()
            : new Ref(scope, named.name());
    return new Ty.Named(named.name(), args, ref);
  }

  private static boolean isReference(final String name) {
    return !name.isEmpty()
        && !Ty.PRIMITIVES.contains(name)
        && name.charAt(0) != '"'
        && !Character.isDigit(name.charAt(0))
        && name.charAt(0) != '-';
  }

  private static List<Ty> all(
      final List<Ty> types, final Map<String, Ty> subst, final Lookup scope) {
    final List<Ty> out = new ArrayList<>();
    for (final Ty type : types) {
      out.add(apply(type, subst, scope));
    }
    return out;
  }

  /** Applies {@code subst} to a signature, leaving the signature's own type parameters alone. */
  static Sig sig(final Sig sig, final Map<String, Ty> subst, final Lookup scope) {
    final Map<String, Ty> inner = new HashMap<>(subst);
    sig.typeParams().forEach(inner::remove);
    final List<Param> params = new ArrayList<>();
    for (final Param param : sig.params()) {
      params.add(
          new Param(
              param.name(), apply(param.type(), inner, scope), param.optional(), param.rest()));
    }
    return new Sig(sig.typeParams(), params, apply(sig.returns(), inner, scope));
  }

  private static List<Member> members(
      final List<Member> members, final Map<String, Ty> subst, final Lookup scope) {
    final List<Member> out = new ArrayList<>();
    for (final Member member : members) {
      out.add(member(member, subst, scope));
    }
    return out;
  }

  /** Applies {@code subst} to a member. */
  static Member member(final Member member, final Map<String, Ty> subst, final Lookup scope) {
    final List<Sig> sigs = new ArrayList<>();
    member.sigs().forEach(sig -> sigs.add(sig(sig, subst, scope)));
    return new Member(
        member.name(),
        apply(member.type(), subst, scope),
        sigs,
        member.flags(),
        member.decorators());
  }
}
