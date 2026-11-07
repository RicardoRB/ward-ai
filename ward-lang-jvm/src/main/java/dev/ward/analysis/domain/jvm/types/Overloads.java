package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chooses the method an invocation calls: the applicable methods of the JLS 15.12.2 phases (by
 * subtyping, with boxing, with variable arity), then the most specific one. When the choice is not
 * unique the call stays unresolved.
 */
final class Overloads {
  private static final int STRICT_PHASE = 1;
  private static final int VARARGS_PHASE = 3;

  private final Assignability assignability;
  private final Inference inference;

  /**
   * An argument of an invocation.
   *
   * @param type its type, empty when unknown
   * @param arity the number of parameters of a lambda, or -1 when it is not a lambda
   * @param reference whether it is a method reference
   */
  record Argument(Optional<JType> type, int arity, boolean reference) {
    static Argument of(final Optional<JType> type) {
      return new Argument(type, -1, false);
    }

    boolean deferred() {
      return arity >= 0 || reference;
    }
  }

  Overloads(final Assignability assignability, final Inference inference) {
    this.assignability = assignability;
    this.inference = inference;
  }

  /** Returns the method called, or empty when none applies or several are equally specific. */
  Optional<Hierarchy.Found<JvmMethod>> select(
      final List<Hierarchy.Found<JvmMethod>> candidates, final List<Argument> arguments) {
    for (int phase = STRICT_PHASE; phase <= VARARGS_PHASE; phase++) {
      final int current = phase;
      final List<Hierarchy.Found<JvmMethod>> applicable =
          candidates.stream()
              .filter(candidate -> applicable(candidate, arguments, current))
              .toList();
      if (!applicable.isEmpty()) {
        return mostSpecific(applicable, phase == VARARGS_PHASE);
      }
    }
    return Optional.empty();
  }

  private boolean applicable(
      final Hierarchy.Found<JvmMethod> candidate, final List<Argument> arguments, final int phase) {
    final List<JType> parameters = parameters(candidate);
    final boolean varargs = phase == VARARGS_PHASE;
    final boolean arity =
        varargs
            ? candidate.member().isVarargs() && arguments.size() >= parameters.size() - 1
            : parameters.size() == arguments.size();
    if (!arity) {
      return false;
    }
    for (int index = 0; index < arguments.size(); index++) {
      final JType parameter = parameterAt(parameters, index, varargs);
      if (!accepts(parameter, arguments.get(index), phase > STRICT_PHASE)) {
        return false;
      }
    }
    return true;
  }

  static JType parameterAt(final List<JType> parameters, final int index, final boolean varargs) {
    final JType last = parameters.get(parameters.size() - 1);
    if (varargs && index >= parameters.size() - 1 && last instanceof JType.ArrayType array) {
      return array.component();
    }
    return parameters.get(index);
  }

  private boolean accepts(final JType parameter, final Argument argument, final boolean loose) {
    if (argument.reference()) {
      return !Types.isPrimitive(parameter);
    }
    if (argument.deferred()) {
      return inference
          .shape(parameter)
          .map(shape -> shape.parameters().size() == argument.arity())
          .orElse(false);
    }
    return argument
        .type()
        .map(type -> assignability.assignable(type, parameter, loose))
        .orElse(true);
  }

  /** The parameter types as the call site sees them: class arguments applied, variables erased. */
  List<JType> parameters(final Hierarchy.Found<JvmMethod> candidate) {
    final Map<String, JType> erased = new HashMap<>(candidate.bindings());
    for (final TypeParameter parameter : candidate.member().typeParameters()) {
      erased.put(
          parameter.name(),
          parameter.bounds().isEmpty()
              ? JType.ClassType.of(Types.OBJECT)
              : parameter.bounds().get(0));
    }
    return candidate.member().parameters().stream()
        .map(parameter -> Types.substitute(parameter, erased))
        .toList();
  }

  private Optional<Hierarchy.Found<JvmMethod>> mostSpecific(
      final List<Hierarchy.Found<JvmMethod>> applicable, final boolean varargs) {
    if (applicable.size() == 1) {
      return Optional.of(applicable.get(0));
    }
    final List<Hierarchy.Found<JvmMethod>> best = new ArrayList<>();
    for (final Hierarchy.Found<JvmMethod> candidate : applicable) {
      if (applicable.stream().allMatch(other -> moreSpecific(candidate, other, varargs))) {
        best.add(candidate);
      }
    }
    if (best.size() == 1) {
      return Optional.of(best.get(0));
    }
    final Set<String> signatures = new HashSet<>();
    applicable.forEach(candidate -> signatures.add(parameters(candidate).toString()));
    return signatures.size() == 1 ? Optional.of(applicable.get(0)) : Optional.empty();
  }

  private boolean moreSpecific(
      final Hierarchy.Found<JvmMethod> first,
      final Hierarchy.Found<JvmMethod> second,
      final boolean varargs) {
    final List<JType> left = parameters(first);
    final List<JType> right = parameters(second);
    final int count = Math.max(left.size(), right.size());
    for (int index = 0; index < count; index++) {
      final JType a = parameterAt(left, Math.min(index, left.size() - 1), varargs);
      final JType b = parameterAt(right, Math.min(index, right.size() - 1), varargs);
      if (!assignability.assignable(a, b, false)) {
        return false;
      }
    }
    return true;
  }
}
