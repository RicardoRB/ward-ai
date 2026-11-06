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

  /** The functional interface method a lambda implements, in terms of the interface's arguments. */
  record Shape(List<JType> parameters, JType returns) {}

  private final Hierarchy hierarchy;

  Overloads(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
  }

  /** Returns the method called, or empty when none applies or several are equally specific. */
  Optional<Hierarchy.Found<JvmMethod>> select(
      final List<Hierarchy.Found<JvmMethod>> candidates, final List<Argument> arguments) {
    for (int phase = 1; phase <= 3; phase++) {
      final int current = phase;
      final List<Hierarchy.Found<JvmMethod>> applicable =
          candidates.stream().filter(candidate -> applicable(candidate, arguments, current)).toList();
      if (!applicable.isEmpty()) {
        return mostSpecific(applicable, phase == 3);
      }
    }
    return Optional.empty();
  }

  private boolean applicable(
      final Hierarchy.Found<JvmMethod> candidate, final List<Argument> arguments, final int phase) {
    final List<JType> parameters = parameters(candidate);
    final boolean varargs = phase == 3;
    if (varargs ? !candidate.member().isVarargs() || arguments.size() < parameters.size() - 1
        : parameters.size() != arguments.size()) {
      return false;
    }
    for (int index = 0; index < arguments.size(); index++) {
      if (!accepts(parameterAt(parameters, index, varargs), arguments.get(index), phase > 1)) {
        return false;
      }
    }
    return true;
  }

  private static JType parameterAt(final List<JType> parameters, final int index, final boolean varargs) {
    if (varargs && index >= parameters.size() - 1
        && parameters.get(parameters.size() - 1) instanceof JType.ArrayType array) {
      return array.component();
    }
    return parameters.get(index);
  }

  private boolean accepts(final JType parameter, final Argument argument, final boolean loose) {
    if (argument.deferred()) {
      if (argument.reference()) {
        return !Types.isPrimitive(parameter);
      }
      return shape(parameter).map(shape -> shape.parameters().size() == argument.arity()).orElse(false);
    }
    return argument.type().map(type -> hierarchy.assignable(type, parameter, loose)).orElse(true);
  }

  /** The parameter types as the call site sees them: class arguments applied, method variables erased. */
  List<JType> parameters(final Hierarchy.Found<JvmMethod> candidate) {
    final Map<String, JType> erased = new HashMap<>(candidate.bindings());
    for (final TypeParameter parameter : candidate.member().typeParameters()) {
      erased.put(parameter.name(), parameter.bounds().isEmpty() ? JType.ClassType.of(Types.OBJECT) : parameter.bounds().get(0));
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
      if (applicable.stream().allMatch(other -> other == candidate || moreSpecific(candidate, other, varargs))) {
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
      final Hierarchy.Found<JvmMethod> first, final Hierarchy.Found<JvmMethod> second, final boolean varargs) {
    final List<JType> left = parameters(first);
    final List<JType> right = parameters(second);
    final int count = Math.max(left.size(), right.size());
    for (int index = 0; index < count; index++) {
      final JType a = parameterAt(left, Math.min(index, left.size() - 1), varargs && index >= left.size() - 1);
      final JType b = parameterAt(right, Math.min(index, right.size() - 1), varargs && index >= right.size() - 1);
      if (!hierarchy.assignable(a, b, false)) {
        return false;
      }
    }
    return true;
  }

  /** Binds the method's type parameters from the argument types. */
  Map<String, JType> infer(final Hierarchy.Found<JvmMethod> chosen, final List<Argument> arguments) {
    final Set<String> variables = new HashSet<>();
    chosen.member().typeParameters().forEach(parameter -> variables.add(parameter.name()));
    final Map<String, JType> bound = new HashMap<>();
    if (variables.isEmpty()) {
      return bound;
    }
    final List<JType> parameters =
        chosen.member().parameters().stream()
            .map(parameter -> Types.substitute(parameter, chosen.bindings()))
            .toList();
    final boolean varargs = chosen.member().isVarargs() && arguments.size() != parameters.size();
    for (int index = 0; index < arguments.size(); index++) {
      final Argument argument = arguments.get(index);
      if (!argument.deferred() && argument.type().isPresent()) {
        unify(parameterAt(parameters, index, varargs), argument.type().get(), variables, bound);
      }
    }
    return bound;
  }

  /** Binds the variables of {@code pattern} that {@code actual} determines. */
  void unify(
      final JType pattern, final JType actual, final Set<String> variables, final Map<String, JType> bound) {
    if (pattern instanceof JType.TypeVariable variable && variables.contains(variable.name())) {
      bind(variable.name(), Types.boxed(actual), bound);
    } else if (pattern instanceof JType.ArrayType array && actual instanceof JType.ArrayType other) {
      unify(array.component(), other.component(), variables, bound);
    } else if (pattern instanceof JType.Wildcard wildcard && wildcard.bound() != null) {
      unify(wildcard.bound(), actual, variables, bound);
    } else if (pattern instanceof JType.ClassType named && actual instanceof JType.ClassType site) {
      hierarchy
          .asSuper(site, named.name())
          .filter(view -> view.arguments().size() == named.arguments().size())
          .ifPresent(
              view -> {
                for (int index = 0; index < named.arguments().size(); index++) {
                  unify(named.arguments().get(index), view.arguments().get(index), variables, bound);
                }
              });
    }
  }

  private void bind(final String name, final JType actual, final Map<String, JType> bound) {
    if (Types.isNull(actual) || actual instanceof JType.Wildcard) {
      return;
    }
    final JType existing = bound.get(name);
    if (existing == null || hierarchy.assignable(existing, actual, true) && !existing.equals(actual)) {
      bound.put(name, actual);
    } else if (!hierarchy.assignable(actual, existing, true)) {
      bound.put(name, JType.ClassType.of(Types.OBJECT));
    }
  }

  /** Returns the method of the functional interface {@code type}, with wildcards resolved. */
  Optional<Shape> shape(final JType type) {
    if (!(type instanceof JType.ClassType site)) {
      return Optional.empty();
    }
    final Optional<JvmClass> owner = hierarchy.find(site.name());
    if (owner.isEmpty() || owner.get().kind() != JvmClass.Kind.INTERFACE) {
      return Optional.empty();
    }
    final List<Hierarchy.Found<JvmMethod>> abstracts = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    collectAbstract(site, abstracts, seen);
    if (abstracts.size() != 1) {
      return Optional.empty();
    }
    final Hierarchy.Found<JvmMethod> single = abstracts.get(0);
    final Map<String, JType> stripped = new HashMap<>();
    single.bindings().forEach((name, value) -> stripped.put(name, strip(value)));
    return Optional.of(
        new Shape(
            single.member().parameters().stream().map(p -> Types.substitute(p, stripped)).toList(),
            Types.substitute(single.member().returnType(), stripped)));
  }

  private static JType strip(final JType type) {
    if (type instanceof JType.Wildcard wildcard) {
      return wildcard.bound() == null ? JType.ClassType.of(Types.OBJECT) : wildcard.bound();
    }
    return type;
  }

  private void collectAbstract(
      final JType.ClassType site, final List<Hierarchy.Found<JvmMethod>> out, final Set<String> seen) {
    final Optional<JvmClass> owner = hierarchy.find(site.name());
    if (owner.isEmpty() || !seen.add(site.name())) {
      return;
    }
    final Map<String, JType> bound = hierarchy.bindings(owner.get(), site);
    for (final JvmMethod method : owner.get().methods()) {
      if ((method.access() & Flags.ABSTRACT) != 0 && !objectMethod(method)
          && out.stream().noneMatch(found -> found.member().name().equals(method.name()))) {
        out.add(new Hierarchy.Found<>(method, owner.get(), bound));
      }
    }
    for (final JType.ClassType parent : owner.get().interfaces()) {
      if (Types.substitute(parent, bound) instanceof JType.ClassType applied) {
        collectAbstract(applied, out, seen);
      }
    }
  }

  private static boolean objectMethod(final JvmMethod method) {
    return "equals".equals(method.name()) && method.parameters().size() == 1
        || "hashCode".equals(method.name()) && method.parameters().isEmpty()
        || "toString".equals(method.name()) && method.parameters().isEmpty();
  }
}
