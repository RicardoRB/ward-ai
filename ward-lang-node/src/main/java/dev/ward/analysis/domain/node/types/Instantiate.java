package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Substitutes type parameters in a type and binds the remaining names to the scope that declared
 * them, so the type can be read from anywhere.
 */
final class Instantiate {
  private Instantiate() {}

  /**
   * Returns {@code type} with the names in {@code subst} replaced and the others bound to {@code
   * scope}; a null scope leaves them unbound.
   */
  static TsType apply(final TsType type, final Map<String, TsType> subst, final Lookup scope) {
    return switch (type) {
      case TsType.Named named -> named(named, subst, scope);
      case TsType.Union union -> new TsType.Union(all(union.parts(), subst, scope));
      case TsType.Inter inter -> new TsType.Inter(all(inter.parts(), subst, scope));
      case TsType.Tuple tuple -> new TsType.Tuple(all(tuple.elements(), subst, scope));
      case TsType.Arr arr -> new TsType.Arr(apply(arr.element(), subst, scope));
      case TsType.Fn fn -> new TsType.Fn(sig(fn.sig(), subst, scope));
      case TsType.Obj obj -> new TsType.Obj(members(obj.members(), subst, scope));
      case TsType.Typeof typeOf -> typeOf;
      case TsType.Unknown unknown -> unknown;
    };
  }

  private static TsType named(
      final TsType.Named named, final Map<String, TsType> subst, final Lookup scope) {
    if (named.args().isEmpty() && subst.containsKey(named.name())) {
      return subst.get(named.name());
    }
    final List<TsType> args = all(named.args(), subst, scope);
    final NameRef ref =
        named.ref() != null || scope == null || !isReference(named.name())
            ? named.ref()
            : new NameRef(scope, named.name());
    return new TsType.Named(named.name(), args, ref);
  }

  private static boolean isReference(final String name) {
    return !name.isEmpty()
        && !TsType.PRIMITIVES.contains(name)
        && name.charAt(0) != '"'
        && !Character.isDigit(name.charAt(0))
        && name.charAt(0) != '-';
  }

  private static List<TsType> all(
      final List<TsType> types, final Map<String, TsType> subst, final Lookup scope) {
    final List<TsType> out = new ArrayList<>();
    for (final TsType type : types) {
      out.add(apply(type, subst, scope));
    }
    return out;
  }

  /** Applies {@code subst} to a signature, leaving the signature's own type parameters alone. */
  static Sig sig(final Sig sig, final Map<String, TsType> subst, final Lookup scope) {
    final Map<String, TsType> inner = new HashMap<>(subst);
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
      final List<Member> members, final Map<String, TsType> subst, final Lookup scope) {
    final List<Member> out = new ArrayList<>();
    for (final Member member : members) {
      out.add(member(member, subst, scope));
    }
    return out;
  }

  /** Applies {@code subst} to a member. */
  static Member member(final Member member, final Map<String, TsType> subst, final Lookup scope) {
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
