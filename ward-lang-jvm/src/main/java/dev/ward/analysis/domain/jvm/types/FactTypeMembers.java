package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the members of a class for the {@code MEMBERS} fact: its methods and fields, every
 * supertype above it and its annotations. The class comes from the project's sources, the jars of
 * its classpath or the JDK, so a type that none of them holds has no members.
 */
final class FactTypeMembers {
  private final Hierarchy hierarchy;

  FactTypeMembers(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
  }

  /** Returns the shape of the type a span declares or whose value it is, if a class holds it. */
  Optional<TypeShape> shape(final Typed typed, final Optional<DeclInfo> declaration) {
    return classOf(typed, declaration).map(FactMembers::shape);
  }

  /** Returns the members of the type a span declares or whose value it is, if a class holds it. */
  Optional<TypeMembers> of(final Typed typed, final Optional<DeclInfo> declaration) {
    return classOf(typed, declaration).map(this::of);
  }

  private Optional<JvmClass> classOf(final Typed typed, final Optional<DeclInfo> declaration) {
    final Optional<JvmClass> declared = declaration.flatMap(DeclInfo::type);
    if (declared.isPresent()) {
      return declared;
    }
    return typed
        .type()
        .filter(JType.ClassType.class::isInstance)
        .map(found -> ((JType.ClassType) found).name())
        .flatMap(hierarchy::find);
  }

  /** Returns the members of {@code type}. */
  TypeMembers of(final JvmClass type) {
    final List<CallTarget> methods = new ArrayList<>();
    for (final JvmMethod method : type.methods()) {
      FactMembers.target(type, method).ifPresent(methods::add);
    }
    return new TypeMembers(
        methods, fields(type), ancestors(type), FactRefs.displayAll(type.annotations()));
  }

  private static List<FieldRef> fields(final JvmClass type) {
    final Map<String, JType> bounds = Map.of();
    return type.fields().stream()
        .map(
            field ->
                new FieldRef(
                    field.name(),
                    FactRefs.erased(field.type(), bounds).orElse(null),
                    field.isStatic(),
                    FactRefs.displayAll(field.annotations())))
        .toList();
  }

  /**
   * Every supertype above {@code type}, the direct ones first, each once, with {@code Object}
   * always among them.
   */
  private List<TypeRef> ancestors(final JvmClass type) {
    final Set<String> seen = new LinkedHashSet<>();
    final Deque<JType.ClassType> pending = new ArrayDeque<>(type.supertypes());
    final Set<String> visited = new HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      if (visited.add(next.name())) {
        seen.add(next.name());
        pending.addAll(hierarchy.directSupertypes(next));
      }
    }
    if (!Types.OBJECT.equals(type.name())) {
      seen.add(Types.OBJECT);
    }
    return seen.stream().map(name -> TypeRef.of(FactRefs.display(name))).toList();
  }
}
