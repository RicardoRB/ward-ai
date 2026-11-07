package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds members among the merged declarations of one name, through aliases and heritage. */
final class SymMembers {
  private final Members members;
  private final Program program;

  SymMembers(final Members members, final Program program) {
    this.members = members;
    this.program = program;
  }

  private Sym global(final String name) {
    return program.globals().find(name);
  }

  Members.Found inSym(final Sym sym, final List<TsType> args, final String name, final int depth) {
    if (sym == null) {
      return null;
    }
    final List<Member> own = new ArrayList<>();
    final List<Sig> sigs = new ArrayList<>();
    Sym.Located owner = null;
    TsType type = TsType.UNKNOWN;
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final Map<String, TsType> subst = MemberValues.substitution(decl.header().typeParams(), args);
      if (decl.kind() == Decl.Kind.ALIAS) {
        final Members.Found viaAlias =
            members.find(Instantiate.apply(decl.type(), subst, located.scope()), name, depth + 1);
        if (viaAlias != null) {
          return viaAlias;
        }
        continue;
      }
      for (final Member member : decl.members()) {
        if (member.name().equals(name) && !member.has(Member.STATIC)) {
          final Member applied = Instantiate.member(member, subst, located.scope());
          own.add(applied);
          sigs.addAll(applied.sigs());
          owner = owner == null ? located : owner;
          type = own.size() == 1 ? applied.type() : type;
        }
      }
    }
    if (!own.isEmpty()) {
      return new Members.Found(own.get(0), sigs, type, owner.decl().name());
    }
    return heritage(sym, args, name, depth);
  }

  private Members.Found heritage(
      final Sym sym, final List<TsType> args, final String name, final int depth) {
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final Map<String, TsType> subst = MemberValues.substitution(decl.header().typeParams(), args);
      for (final TsType base : decl.header().extendsTypes()) {
        final Members.Found found =
            members.find(Instantiate.apply(base, subst, located.scope()), name, depth + 1);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  Members.Found statics(final TsType.Typeof typeOf, final String name, final int depth) {
    final Sym sym = typeOf.ref() == null ? null : typeOf.ref().sym();
    if (sym == null) {
      return null;
    }
    for (final Sym.Located located : sym.parts()) {
      final Decl decl = located.decl();
      for (final Member member : decl.members()) {
        final boolean isStatic = decl.kind() == Decl.Kind.ENUM || member.has(Member.STATIC);
        if (isStatic && member.name().equals(name)) {
          final Member applied = Instantiate.member(member, new HashMap<>(), located.scope());
          return new Members.Found(applied, applied.sigs(), applied.type(), decl.name());
        }
      }
    }
    final Sym namespaced = Names.member(sym, name);
    if (namespaced != null) {
      final TsType value = MemberValues.valueOf(namespaced, name);
      return new Members.Found(
          new Member(name, value, List.of(), 0, List.of()), List.of(), value, typeOf.name());
    }
    return sym.of(Decl.Kind.CLASS).isEmpty()
        ? null
        : inSym(global("Function"), List.of(), name, depth + 1);
  }
}
