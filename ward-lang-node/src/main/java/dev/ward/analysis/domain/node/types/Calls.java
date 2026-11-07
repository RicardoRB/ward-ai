package dev.ward.analysis.domain.node.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Picks the overload a call resolves to and infers its type arguments from the arguments. */
final class Calls {
  private Calls() {}

  /**
   * The overload a call resolves to.
   *
   * @param sig the overload as declared
   * @param returns its return type with the inferred type arguments applied
   */
  record Picked(Sig sig, TsType returns) {}

  /** Returns the first overload of {@code sigs} that accepts {@code args}. */
  static Optional<Picked> pick(final List<Sig> sigs, final List<TsType> args) {
    for (final Sig sig : sigs) {
      if (sig.accepts(args.size()) && compatible(sig, args)) {
        return Optional.of(new Picked(sig, returns(sig, args)));
      }
    }
    return Optional.empty();
  }

  private static boolean compatible(final Sig sig, final List<TsType> args) {
    for (int index = 0; index < args.size(); index++) {
      final Param param = parameter(sig, index);
      if (param != null && clash(param.type(), args.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static Param parameter(final Sig sig, final int index) {
    if (index < sig.params().size()) {
      return sig.params().get(index);
    }
    return sig.isVarargs() ? sig.params().get(sig.params().size() - 1) : null;
  }

  /** Whether both types are known primitives that differ, so the argument cannot be passed. */
  private static boolean clash(final TsType declared, final TsType argument) {
    final TsType expected =
        declared instanceof TsType.Arr arr && !arr.element().isPrimitive() ? declared : declared;
    if (!expected.isPrimitive() || !Literals.widen(argument).isPrimitive()) {
      return false;
    }
    return !Literals.widen(argument).print().equals(declared.print());
  }

  private static TsType returns(final Sig sig, final List<TsType> args) {
    if (sig.typeParams().isEmpty()) {
      return sig.returns();
    }
    final Map<String, TsType> inferred = new HashMap<>();
    for (int index = 0; index < args.size(); index++) {
      final Param param = parameter(sig, index);
      if (param != null) {
        final TsType declared =
            param.rest() && param.type() instanceof TsType.Arr arr ? arr.element() : param.type();
        unify(declared, args.get(index), sig.typeParams(), inferred);
      }
    }
    sig.typeParams().forEach(name -> inferred.putIfAbsent(name, TsType.UNKNOWN));
    return Instantiate.apply(sig.returns(), inferred, null);
  }

  private static void unify(
      final TsType declared,
      final TsType argument,
      final List<String> params,
      final Map<String, TsType> out) {
    if (declared instanceof TsType.Named named) {
      unifyNamed(named, argument, params, out);
    } else if (declared instanceof TsType.Arr arr && argument instanceof TsType.Arr other) {
      unify(arr.element(), other.element(), params, out);
    } else if (declared instanceof TsType.Fn fn && argument instanceof TsType.Fn other) {
      unify(fn.sig().returns(), other.sig().returns(), params, out);
    } else if (declared instanceof TsType.Union union) {
      union.parts().stream()
          .filter(part -> !MemberValues.isNullish(part))
          .forEach(part -> unify(part, argument, params, out));
    }
  }

  private static void unifyNamed(
      final TsType.Named named,
      final TsType argument,
      final List<String> params,
      final Map<String, TsType> out) {
    if (named.args().isEmpty() && params.contains(named.name())) {
      out.putIfAbsent(named.name(), Literals.widen(argument));
    } else if (argument instanceof TsType.Named other && other.name().equals(named.name())) {
      pairwise(named.args(), other.args(), params, out);
    }
  }

  private static void pairwise(
      final List<TsType> declared,
      final List<TsType> given,
      final List<String> params,
      final Map<String, TsType> out) {
    for (int index = 0; index < Math.min(declared.size(), given.size()); index++) {
      unify(declared.get(index), given.get(index), params, out);
    }
  }
}
