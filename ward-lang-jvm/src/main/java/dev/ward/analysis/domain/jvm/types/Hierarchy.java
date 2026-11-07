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

/** Supertypes and members over the classes a unit can see. */
final class Hierarchy {
  private final JvmClasses classes;

  Hierarchy(final JvmClasses classes) {
    this.classes = classes;
  }

  /**
   * A member found on a type, with the class that declares it and the type arguments of that class
   * as seen from the site of the lookup.
   */
  record Found<T>(T member, JvmClass owner, Map<String, JType> bindings) {}

  Optional<JvmClass> find(final String name) {
    return classes.find(name);
  }

  boolean exists(final String name) {
    return classes.exists(name);
  }

  /**
   * Maps the type parameters of {@code owner} to the arguments of {@code site}; raw types erase.
   */
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
                      : Types.erasedName(parameter.bounds().get(0))));
    }
    return bound;
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
        final Optional<Found<JvmField>> field =
            MemberViews.field(owner.get(), name, bindings(owner.get(), next));
        if (field.isPresent()) {
          return field;
        }
        pending.addAll(directSupertypes(next));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the methods named {@code name} on {@code site}, nearest first, overridden ones removed.
   */
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
      found.addAll(MemberViews.methods(owner.get(), bindings(owner.get(), next), name, signatures));
      if (!JvmMethod.CONSTRUCTOR.equals(name)) {
        pending.addAll(directSupertypes(next));
      }
    }
    return found;
  }

  /**
   * Returns the binary name of the member class {@code name} of {@code owner} or its supertypes.
   */
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
}
