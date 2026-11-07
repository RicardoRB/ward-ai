package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The signatures that calling a value can use. */
final class Callables {
  private Callables() {}

  /** Returns the signatures that calling a value of {@code type} can use. */
  static List<Sig> callable(final TsType type) {
    return switch (type) {
      case TsType.Fn fn -> List.of(fn.sig());
      case TsType.Typeof typeOf -> functionSigs(typeOf);
      case TsType.Named named -> interfaceCalls(named);
      case TsType.Obj obj ->
          obj.members().stream()
              .filter(member -> "()".equals(member.name()))
              .flatMap(m -> m.sigs().stream())
              .toList();
      default -> List.of();
    };
  }

  private static List<Sig> functionSigs(final TsType.Typeof typeOf) {
    final Sym sym = typeOf.ref() == null ? null : typeOf.ref().sym();
    final List<Sig> sigs = new ArrayList<>();
    if (sym != null) {
      for (final Sym.Located located : sym.of(Decl.Kind.FUNCTION)) {
        located
            .decl()
            .header()
            .calls()
            .forEach(sig -> sigs.add(Instantiate.sig(sig, Map.of(), located.scope())));
      }
    }
    return sigs;
  }

  private static List<Sig> interfaceCalls(final TsType.Named named) {
    final List<Sig> sigs = new ArrayList<>();
    final Sym sym = named.ref() == null ? null : named.ref().sym();
    if (sym == null) {
      return sigs;
    }
    for (final Sym.Located located : sym.types()) {
      final Map<String, TsType> subst =
          MemberValues.substitution(located.decl().header().typeParams(), named.args());
      located
          .decl()
          .header()
          .calls()
          .forEach(sig -> sigs.add(Instantiate.sig(sig, subst, located.scope())));
    }
    return sigs;
  }
}
