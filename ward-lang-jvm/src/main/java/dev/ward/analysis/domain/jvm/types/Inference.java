package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Infers the type arguments of a generic invocation from its arguments, and reads the shape of the
 * functional interface a lambda implements.
 */
final class Inference {
  /** The functional interface method a lambda implements, in terms of the interface's arguments. */
  record Shape(List<JType> parameters, JType returns) {}

  private final Hierarchy hierarchy;
  private final Assignability assignability;

  Inference(final Hierarchy hierarchy, final Assignability assignability) {
    this.hierarchy = hierarchy;
    this.assignability = assignability;
  }

  /** Binds the method's type parameters from the argument types. */
  Map<String, JType> infer(
      final Hierarchy.Found<JvmMethod> chosen, final List<Overloads.Argument> arguments) {
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
      final Overloads.Argument argument = arguments.get(index);
      if (!argument.deferred() && argument.type().isPresent()) {
        unify(Overloads.parameterAt(parameters, index, varargs), argument.type().get(), variables, bound);
      }
    }
    return bound;
  }

  /** Binds the variables of {@code pattern} that {@code actual} determines. */
  void unify(
      final JType pattern,
      final JType actual,
      final Set<String> variables,
      final Map<String, JType> bound) {
    if (pattern instanceof JType.TypeVariable variable && variables.contains(variable.name())) {
      bind(variable.name(), Types.boxed(actual), bound);
    } else if (pattern instanceof JType.ArrayType array && actual instanceof JType.ArrayType other) {
      unify(array.component(), other.component(), variables, bound);
    } else if (pattern instanceof JType.Wildcard wildcard && wildcard.bound() != null) {
      unify(wildcard.bound(), actual, variables, bound);
    } else if (pattern instanceof JType.ClassType named && actual instanceof JType.ClassType site) {
      unifyArguments(named, site, variables, bound);
    }
  }

  private void unifyArguments(
      final JType.ClassType pattern,
      final JType.ClassType site,
      final Set<String> variables,
      final Map<String, JType> bound) {
    final Optional<JType.ClassType> view = hierarchy.asSuper(site, pattern.name());
    if (view.isEmpty() || view.get().arguments().size() != pattern.arguments().size()) {
      return;
    }
    for (int index = 0; index < pattern.arguments().size(); index++) {
      unify(pattern.arguments().get(index), view.get().arguments().get(index), variables, bound);
    }
  }

  private void bind(final String name, final JType actual, final Map<String, JType> bound) {
    if (Types.isNull(actual) || actual instanceof JType.Wildcard) {
      return;
    }
    final JType existing = bound.get(name);
    if (existing == null
        || assignability.assignable(existing, actual, true) && !existing.equals(actual)) {
      bound.put(name, actual);
    } else if (!assignability.assignable(actual, existing, true)) {
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
    collectAbstract(site, abstracts, new HashSet<>());
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
      final JType.ClassType site,
      final List<Hierarchy.Found<JvmMethod>> out,
      final Set<String> seen) {
    final Optional<JvmClass> owner = hierarchy.find(site.name());
    if (owner.isEmpty() || !seen.add(site.name())) {
      return;
    }
    final Map<String, JType> bound = hierarchy.bindings(owner.get(), site);
    for (final JvmMethod method : owner.get().methods()) {
      if (Flags.has(method.access(), Flags.ABSTRACT)
          && !objectMethod(method)
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
    final int arity = method.parameters().size();
    return "equals".equals(method.name()) && arity == 1
        || "hashCode".equals(method.name()) && arity == 0
        || "toString".equals(method.name()) && arity == 0;
  }
}
