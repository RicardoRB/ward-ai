package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Supertypes, members and assignability over the classes a unit can see. */
final class Hierarchy {
  /**
   * A member found on a type, with the class that declares it and the type arguments of that class
   * as seen from the site of the lookup.
   */
  record Found<T>(T member, JvmClass owner, Map<String, JType> bindings) {}

  private final JvmClasses classes;

  Hierarchy(final JvmClasses classes) {
    this.classes = classes;
  }

  Optional<JvmClass> find(final String name) {
    return classes.find(name);
  }

  boolean exists(final String name) {
    return classes.exists(name);
  }

  /** Maps the type parameters of {@code owner} to the arguments of {@code site}; raw types erase. */
  Map<String, JType> bindings(final JvmClass owner, final JType.ClassType site) {
    final Map<String, JType> bound = new HashMap<>();
    final List<TypeParameter> parameters = owner.typeParameters();
    for (int index = 0; index < parameters.size(); index++) {
      final TypeParameter parameter = parameters.get(index);
      bound.put(
          parameter.name(),
          site.arguments().size() == parameters.size()
              ? site.arguments().get(index)
              : JType.ClassType.of(
                  parameter.bounds().isEmpty()
                      ? Types.OBJECT
                      : erasureName(parameter.bounds().get(0))));
    }
    return bound;
  }

  private static String erasureName(final JType type) {
    return type instanceof JType.ClassType named ? named.name() : Types.OBJECT;
  }

  /** Returns the direct supertypes of {@code site} with its arguments applied. */
  List<JType.ClassType> directSupertypes(final JType.ClassType site) {
    final Optional<JvmClass> owner = find(site.name());
    if (owner.isEmpty()) {
      return List.of();
    }
    final Map<String, JType> bound = bindings(owner.get(), site);
    final List<JType.ClassType> supers = new ArrayList<>();
    for (final JType.ClassType declared : owner.get().supertypes()) {
      if (Types.substitute(declared, bound) instanceof JType.ClassType applied) {
        supers.add(applied);
      }
    }
    if (owner.get().superclass().isEmpty() && !Types.OBJECT.equals(site.name())) {
      supers.add(0, JType.ClassType.of(Types.OBJECT));
    }
    return supers;
  }

  /** Returns {@code type} viewed as the class {@code target}, such as an ArrayList as a List. */
  Optional<JType.ClassType> asSuper(final JType.ClassType type, final String target) {
    final Deque<JType.ClassType> pending = new ArrayDeque<>(List.of(type));
    final Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      if (next.name().equals(target)) {
        return Optional.of(next);
      }
      if (seen.add(next.name())) {
        pending.addAll(directSupertypes(next));
      }
    }
    return Optional.empty();
  }

  boolean isSubclass(final String sub, final String sup) {
    return Types.OBJECT.equals(sup) || asSuper(JType.ClassType.of(sub), sup).isPresent();
  }

  /** Returns the field {@code name} on {@code site} or its supertypes. */
  Optional<Found<JvmField>> field(final JType.ClassType site, final String name) {
    final Deque<JType.ClassType> pending = new ArrayDeque<>(List.of(site));
    final Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      final Optional<JvmClass> owner = find(next.name());
      if (owner.isPresent() && seen.add(next.name())) {
        final Optional<JvmField> field =
            owner.get().fields().stream().filter(candidate -> candidate.name().equals(name)).findFirst();
        if (field.isPresent()) {
          return Optional.of(new Found<>(field.get(), owner.get(), bindings(owner.get(), next)));
        }
        pending.addAll(directSupertypes(next));
      }
    }
    return Optional.empty();
  }

  /** Returns the methods named {@code name} on {@code site}, nearest first, overridden ones removed. */
  List<Found<JvmMethod>> methods(final JType.ClassType site, final String name) {
    final List<Found<JvmMethod>> found = new ArrayList<>();
    final Set<String> signatures = new HashSet<>();
    final Deque<JType.ClassType> pending = new ArrayDeque<>(List.of(site));
    final Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      final Optional<JvmClass> owner = find(next.name());
      if (owner.isEmpty() || !seen.add(next.name())) {
        continue;
      }
      final Map<String, JType> bound = bindings(owner.get(), next);
      for (final JvmMethod method : owner.get().methods()) {
        if (method.name().equals(name) && (method.access() & Flags.SYNTHETIC) == 0
            && signatures.add(signature(method, bound))) {
          final Map<String, JType> visible = new HashMap<>(bound);
          method.typeParameters().forEach(parameter -> visible.remove(parameter.name()));
          found.add(new Found<>(method, owner.get(), visible));
        }
      }
      if (!JvmMethod.CONSTRUCTOR.equals(name)) {
        pending.addAll(directSupertypes(next));
      }
    }
    return found;
  }

  private String signature(final JvmMethod method, final Map<String, JType> bound) {
    final StringBuilder text = new StringBuilder();
    for (final JType parameter : method.parameters()) {
      text.append(erasure(Types.substitute(parameter, bound), Map.of())).append(',');
    }
    return text.toString();
  }

  /** Returns the binary name of the member class {@code name} of {@code owner} or its supertypes. */
  Optional<String> memberClass(final String owner, final String name) {
    final Deque<JType.ClassType> pending = new ArrayDeque<>(List.of(JType.ClassType.of(owner)));
    final Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      if (classes.exists(next.name() + '$' + name)) {
        return Optional.of(next.name() + '$' + name);
      }
      if (seen.add(next.name())) {
        pending.addAll(directSupertypes(next));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the erasure of {@code type} as a string: a class name, a primitive, or either with
   * brackets. Type variables erase to their bound in {@code bounds}, else to {@code Object}.
   */
  String erasure(final JType type, final Map<String, JType> bounds) {
    return switch (type) {
      case JType.Primitive primitive -> primitive.name();
      case JType.ClassType named -> named.name();
      case JType.ArrayType array -> erasure(array.component(), bounds) + "[]";
      case JType.TypeVariable variable ->
          bounds.containsKey(variable.name()) ? erasure(bounds.get(variable.name()), Map.of()) : Types.OBJECT;
      case JType.Wildcard wildcard ->
          wildcard.bound() != null && wildcard.upper() ? erasure(wildcard.bound(), bounds) : Types.OBJECT;
    };
  }

  /** Returns whether a value of type {@code from} can be passed where {@code to} is expected. */
  boolean assignable(final JType from, final JType to, final boolean loose) {
    if (from instanceof JType.TypeVariable || to instanceof JType.TypeVariable) {
      return true;
    }
    if (Types.isNull(from)) {
      return !Types.isPrimitive(to);
    }
    if (Types.isPrimitive(to)) {
      final JType source = loose ? Types.unboxed(from) : from;
      return source instanceof JType.Primitive a
          && to instanceof JType.Primitive b
          && Types.widens(a.name(), b.name());
    }
    if (Types.isPrimitive(from)) {
      return loose && assignable(Types.boxed(from), to, false);
    }
    return referenceAssignable(from, to);
  }

  private boolean referenceAssignable(final JType from, final JType to) {
    final JType target = to instanceof JType.Wildcard wildcard && wildcard.bound() != null ? wildcard.bound() : to;
    if (target instanceof JType.ClassType named && Types.OBJECT.equals(named.name())) {
      return true;
    }
    if (from instanceof JType.ArrayType source) {
      if (target instanceof JType.ArrayType array) {
        return Types.isPrimitive(source.component())
            ? source.component().equals(array.component())
            : assignable(source.component(), array.component(), false);
      }
      return target instanceof JType.ClassType named
          && ("java.lang.Cloneable".equals(named.name()) || "java.io.Serializable".equals(named.name()));
    }
    return from instanceof JType.ClassType source
        && target instanceof JType.ClassType named
        && isSubclass(source.name(), named.name());
  }
}
