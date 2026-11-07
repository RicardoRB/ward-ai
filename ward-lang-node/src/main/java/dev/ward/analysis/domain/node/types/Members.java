package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds the members of types: through merged declarations, heritage and type arguments. */
final class Members {
  private static final int MAX_DEPTH = 12;
  private static final String ARRAY = "Array";

  private final Program program;

  Members(final Program program) {
    this.program = program;
  }

  /**
   * A member found on a type.
   *
   * @param member the member, with all its overloads
   * @param sigs the overloads with the type arguments of the receiver applied
   * @param type the member's type with the type arguments of the receiver applied
   * @param owner the name of the type that declares it
   */
  record Found(Member member, List<Sig> sigs, Ty type, String owner) {}

  /** Returns the instance member {@code name} of {@code receiver}, or null. */
  Found find(final Ty receiver, final String name) {
    return find(receiver, name, 0);
  }

  private Found find(final Ty receiver, final String name, final int depth) {
    if (depth > MAX_DEPTH) {
      return null;
    }
    return switch (receiver) {
      case Ty.Named named -> named(named, name, depth);
      case Ty.Obj obj -> object(obj, name);
      case Ty.Union union -> union(union, name, depth);
      case Ty.Inter inter -> inter(inter, name, depth);
      case Ty.Typeof typeOf -> statics(typeOf, name, depth);
      case Ty.Unknown unknown -> null;
      default -> viaGlobal(receiver, name, depth);
    };
  }

  /** Arrays, tuples and functions have the members of the global interfaces of the library. */
  private Found viaGlobal(final Ty receiver, final String name, final int depth) {
    if (receiver instanceof Ty.Arr arr) {
      return inSym(global(ARRAY), List.of(arr.element()), name, depth);
    }
    if (receiver instanceof Ty.Tuple tuple) {
      return inSym(global(ARRAY), List.of(common(tuple.elements())), name, depth);
    }
    return inSym(global("Function"), List.of(), name, depth);
  }

  private Sym global(final String name) {
    return program.globals().find(name);
  }

  private static Ty common(final List<Ty> types) {
    final boolean same = types.stream().map(Ty::print).distinct().count() == 1;
    return same ? types.get(0) : Ty.UNKNOWN;
  }

  private Found named(final Ty.Named named, final String name, final int depth) {
    final String boxed = boxed(named.name());
    final Sym sym = boxed != null ? global(boxed) : named.ref() == null ? null : named.ref().sym();
    return sym == null ? null : inSym(sym, named.args(), name, depth);
  }

  private static String boxed(final String name) {
    if (name.isEmpty()) {
      return null;
    }
    if ("string".equals(name) || name.charAt(0) == '"') {
      return "String";
    }
    if ("number".equals(name) || Character.isDigit(name.charAt(0)) || name.charAt(0) == '-') {
      return "Number";
    }
    return switch (name) {
      case "boolean", "true", "false" -> "Boolean";
      case "bigint" -> "BigInt";
      case "symbol" -> "Symbol";
      default -> null;
    };
  }

  private Found object(final Ty.Obj obj, final String name) {
    for (final Member member : obj.members()) {
      if (member.name().equals(name)) {
        return new Found(member, member.sigs(), member.type(), obj.print());
      }
    }
    return null;
  }

  private Found union(final Ty.Union union, final String name, final int depth) {
    final List<Ty> types = new ArrayList<>();
    Found first = null;
    for (final Ty part : union.parts()) {
      if (isNullish(part)) {
        continue;
      }
      final Found found = find(part, name, depth + 1);
      if (found == null || !found.type().isKnown()) {
        return null;
      }
      first = first == null ? found : first;
      if (types.stream().noneMatch(seen -> seen.print().equals(found.type().print()))) {
        types.add(found.type());
      }
    }
    if (first == null) {
      return null;
    }
    final Ty type = types.size() == 1 ? types.get(0) : new Ty.Union(types);
    return new Found(
        first.member(), types.size() == 1 ? first.sigs() : List.of(), type, first.owner());
  }

  private Found inter(final Ty.Inter inter, final String name, final int depth) {
    for (final Ty part : inter.parts()) {
      final Found found = find(part, name, depth + 1);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  static boolean isNullish(final Ty type) {
    return type instanceof Ty.Named named
        && named.args().isEmpty()
        && ("null".equals(named.name()) || "undefined".equals(named.name()));
  }

  private Found inSym(final Sym sym, final List<Ty> args, final String name, final int depth) {
    if (sym == null) {
      return null;
    }
    final List<Member> own = new ArrayList<>();
    final List<Sig> sigs = new ArrayList<>();
    Sym.Located owner = null;
    Ty type = Ty.UNKNOWN;
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final Map<String, Ty> subst = substitution(decl.header().typeParams(), args);
      if (decl.kind() == Decl.Kind.ALIAS) {
        final Found viaAlias =
            find(Inst.apply(decl.type(), subst, located.scope()), name, depth + 1);
        if (viaAlias != null) {
          return viaAlias;
        }
        continue;
      }
      for (final Member member : decl.members()) {
        if (member.name().equals(name) && !member.has(Member.STATIC)) {
          final Member applied = Inst.member(member, subst, located.scope());
          own.add(applied);
          sigs.addAll(applied.sigs());
          owner = owner == null ? located : owner;
          type = own.size() == 1 ? applied.type() : type;
        }
      }
    }
    if (!own.isEmpty()) {
      return new Found(own.get(0), sigs, type, owner.decl().name());
    }
    return heritage(sym, args, name, depth);
  }

  private Found heritage(final Sym sym, final List<Ty> args, final String name, final int depth) {
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final Map<String, Ty> subst = substitution(decl.header().typeParams(), args);
      for (final Ty base : decl.header().extendsTypes()) {
        final Found found = find(Inst.apply(base, subst, located.scope()), name, depth + 1);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /** Maps type parameters to arguments; a missing argument is unknown. */
  static Map<String, Ty> substitution(final List<String> params, final List<Ty> args) {
    final Map<String, Ty> subst = new LinkedHashMap<>();
    for (int index = 0; index < params.size(); index++) {
      subst.put(params.get(index), index < args.size() ? args.get(index) : Ty.UNKNOWN);
    }
    return subst;
  }

  private Found statics(final Ty.Typeof typeOf, final String name, final int depth) {
    final Sym sym = typeOf.ref() == null ? null : typeOf.ref().sym();
    if (sym == null) {
      return null;
    }
    for (final Sym.Located located : sym.parts()) {
      final Decl decl = located.decl();
      for (final Member member : decl.members()) {
        final boolean isStatic = decl.kind() == Decl.Kind.ENUM || member.has(Member.STATIC);
        if (isStatic && member.name().equals(name)) {
          final Member applied = Inst.member(member, new HashMap<>(), located.scope());
          return new Found(applied, applied.sigs(), applied.type(), decl.name());
        }
      }
    }
    final Sym namespaced = Names.member(sym, name);
    if (namespaced != null) {
      final Ty value = valueOf(namespaced, name);
      return new Found(
          new Member(name, value, List.of(), 0, List.of()), List.of(), value, typeOf.name());
    }
    return sym.of(Decl.Kind.CLASS).isEmpty()
        ? null
        : inSym(global("Function"), List.of(), name, depth + 1);
  }

  /** Returns the type of the value that {@code sym} declares, or {@link Ty#UNKNOWN}. */
  static Ty valueOf(final Sym sym, final String name) {
    if (sym == null) {
      return Ty.UNKNOWN;
    }
    for (final Sym.Located located : sym.of(Decl.Kind.VARIABLE)) {
      return Inst.apply(located.decl().type(), Map.of(), located.scope());
    }
    final boolean isValue = sym.parts().stream().anyMatch(located -> located.decl().isValue());
    return isValue ? new Ty.Typeof(name, new Ref(sym, name)) : Ty.UNKNOWN;
  }
}
