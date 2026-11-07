package dev.ward.analysis.domain.node.types;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers split out of {@link Members}. */
final class MemberValues {

  private MemberValues() {}

  static TsType common(final List<TsType> types) {
    final boolean same = types.stream().map(TsType::print).distinct().count() == 1;
    return same ? types.get(0) : TsType.UNKNOWN;
  }

  static String boxed(final String name) {
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

  static boolean isNullish(final TsType type) {
    return type instanceof TsType.Named named
        && named.args().isEmpty()
        && ("null".equals(named.name()) || "undefined".equals(named.name()));
  }

  /** Maps type parameters to arguments; a missing argument is unknown. */
  static Map<String, TsType> substitution(final List<String> params, final List<TsType> args) {
    final Map<String, TsType> subst = new LinkedHashMap<>();
    for (int index = 0; index < params.size(); index++) {
      subst.put(params.get(index), index < args.size() ? args.get(index) : TsType.UNKNOWN);
    }
    return subst;
  }

  /** Returns the type of the value that {@code sym} declares, or {@link TsType#UNKNOWN}. */
  static TsType valueOf(final Sym sym, final String name) {
    if (sym == null) {
      return TsType.UNKNOWN;
    }
    for (final Sym.Located located : sym.of(Decl.Kind.VARIABLE)) {
      return Instantiate.apply(located.decl().type(), Map.of(), located.scope());
    }
    final boolean isValue = sym.parts().stream().anyMatch(located -> located.decl().isValue());
    return isValue ? new TsType.Typeof(name, new NameRef(sym, name)) : TsType.UNKNOWN;
  }
}
