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
  record Picked(Sig sig, Ty returns) {}

  /** Returns the first overload of {@code sigs} that accepts {@code args}. */
  static Optional<Picked> pick(final List<Sig> sigs, final List<Ty> args) {
    for (final Sig sig : sigs) {
      if (sig.accepts(args.size()) && compatible(sig, args)) {
        return Optional.of(new Picked(sig, returns(sig, args)));
      }
    }
    return Optional.empty();
  }

  private static boolean compatible(final Sig sig, final List<Ty> args) {
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
  private static boolean clash(final Ty declared, final Ty argument) {
    final Ty expected =
        declared instanceof Ty.Arr arr && !arr.element().isPrimitive() ? declared : declared;
    if (!expected.isPrimitive() || !Literals.widen(argument).isPrimitive()) {
      return false;
    }
    return !Literals.widen(argument).print().equals(expected.print());
  }

  private static Ty returns(final Sig sig, final List<Ty> args) {
    if (sig.typeParams().isEmpty()) {
      return sig.returns();
    }
    final Map<String, Ty> inferred = new HashMap<>();
    for (int index = 0; index < args.size(); index++) {
      final Param param = parameter(sig, index);
      if (param != null) {
        final Ty declared =
            param.rest() && param.type() instanceof Ty.Arr arr ? arr.element() : param.type();
        unify(declared, args.get(index), sig.typeParams(), inferred);
      }
    }
    sig.typeParams().forEach(name -> inferred.putIfAbsent(name, Ty.UNKNOWN));
    return Inst.apply(sig.returns(), inferred, null);
  }

  private static void unify(
      final Ty declared, final Ty argument, final List<String> params, final Map<String, Ty> out) {
    switch (declared) {
      case Ty.Named named when named.args().isEmpty() && params.contains(named.name()) ->
          out.putIfAbsent(named.name(), Literals.widen(argument));
      case Ty.Named named
          when argument instanceof Ty.Named other && other.name().equals(named.name()) ->
          pairwise(named.args(), other.args(), params, out);
      case Ty.Arr arr when argument instanceof Ty.Arr other ->
          unify(arr.element(), other.element(), params, out);
      case Ty.Fn fn when argument instanceof Ty.Fn other ->
          unify(fn.sig().returns(), other.sig().returns(), params, out);
      case Ty.Union union ->
          union.parts().stream()
              .filter(part -> !Members.isNullish(part))
              .forEach(part -> unify(part, argument, params, out));
      default -> {
        // nothing to learn from this pair
      }
    }
  }

  private static void pairwise(
      final List<Ty> declared,
      final List<Ty> given,
      final List<String> params,
      final Map<String, Ty> out) {
    for (int index = 0; index < Math.min(declared.size(), given.size()); index++) {
      unify(declared.get(index), given.get(index), params, out);
    }
  }
}
