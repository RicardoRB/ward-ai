package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;

/** Finds the members of types: through merged declarations, heritage and type arguments. */
final class Members {
  private static final int MAX_DEPTH = 12;
  private static final String ARRAY = "Array";

  private final Program program;
  private final SymMembers symbols;

  Members(final Program program) {
    this.program = program;
    this.symbols = new SymMembers(this, program);
  }

  /**
   * A member found on a type.
   *
   * @param member the member, with all its overloads
   * @param sigs the overloads with the type arguments of the receiver applied
   * @param type the member's type with the type arguments of the receiver applied
   * @param owner the name of the type that declares it
   */
  record Found(Member member, List<Sig> sigs, TsType type, String owner) {}

  /** Returns the instance member {@code name} of {@code receiver}, or null. */
  Found find(final TsType receiver, final String name) {
    return find(receiver, name, 0);
  }

  Found find(final TsType receiver, final String name, final int depth) {
    if (depth > MAX_DEPTH) {
      return null;
    }
    return switch (receiver) {
      case TsType.Named named -> named(named, name, depth);
      case TsType.Obj obj -> object(obj, name);
      case TsType.Union union -> union(union, name, depth);
      case TsType.Inter inter -> inter(inter, name, depth);
      case TsType.Typeof typeOf -> symbols.statics(typeOf, name, depth);
      case TsType.Unknown unknown -> null;
      default -> viaGlobal(receiver, name, depth);
    };
  }

  /** Arrays, tuples and functions have the members of the global interfaces of the library. */
  private Found viaGlobal(final TsType receiver, final String name, final int depth) {
    if (receiver instanceof TsType.Arr arr) {
      return symbols.inSym(global(ARRAY), List.of(arr.element()), name, depth);
    }
    if (receiver instanceof TsType.Tuple tuple) {
      return symbols.inSym(
          global(ARRAY), List.of(MemberValues.common(tuple.elements())), name, depth);
    }
    return symbols.inSym(global("Function"), List.of(), name, depth);
  }

  private Sym global(final String name) {
    return program.globals().find(name);
  }

  private Found named(final TsType.Named named, final String name, final int depth) {
    final String boxed = MemberValues.boxed(named.name());
    final Sym sym = boxed != null ? global(boxed) : named.ref() == null ? null : named.ref().sym();
    return sym == null ? null : symbols.inSym(sym, named.args(), name, depth);
  }

  private Found object(final TsType.Obj obj, final String name) {
    for (final Member member : obj.members()) {
      if (member.name().equals(name)) {
        return new Found(member, member.sigs(), member.type(), obj.print());
      }
    }
    return null;
  }

  private Found union(final TsType.Union union, final String name, final int depth) {
    final List<TsType> types = new ArrayList<>();
    Found first = null;
    for (final TsType part : union.parts()) {
      if (MemberValues.isNullish(part)) {
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
    final TsType type = types.size() == 1 ? types.get(0) : new TsType.Union(types);
    return new Found(
        first.member(), types.size() == 1 ? first.sigs() : List.of(), type, first.owner());
  }

  private Found inter(final TsType.Inter inter, final String name, final int depth) {
    for (final TsType part : inter.parts()) {
      final Found found = find(part, name, depth + 1);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
}
