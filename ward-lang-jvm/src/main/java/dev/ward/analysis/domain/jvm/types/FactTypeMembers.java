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
  private final FactComponents components;

  FactTypeMembers(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
    this.components = new FactComponents(hierarchy, this);
  }

  /** Returns the shape of the type a span declares or whose value it is, if a class holds it. */
  Optional<TypeShape> shape(final Typed typed, final Optional<DeclInfo> declaration) {
    return classOf(typed, declaration).map(FactMembers::shape);
  }

  /** Returns the members of the type a span declares or whose value it is, if a class holds it. */
  Optional<TypeMembers> of(final Typed typed, final Optional<DeclInfo> declaration) {
    return classOf(typed, declaration)
        .map(this::of)
        .or(() -> components.arrayOnly(typed, declaration))
        .map(members -> components.add(members, typed, declaration));
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
    final List<JvmClass> above = ancestorClasses(type);
    final List<CallTarget> inheritedMethods = new ArrayList<>();
    final List<FieldRef> inheritedFields = new ArrayList<>();
    for (final JvmClass owner : above) {
      InheritedMemberReader.methods(owner).forEach(inheritedMethods::add);
      InheritedMemberReader.fields(owner).forEach(inheritedFields::add);
    }
    return new TypeMembers(
        methods,
        fields(type),
        ancestors(type),
        FactRefs.displayAll(type.annotations()),
        inheritedMethods,
        inheritedFields,
        List.of());
  }

  /** Returns the classes of the ancestors of {@code type} that the hierarchy holds. */
  private List<JvmClass> ancestorClasses(final JvmClass type) {
    return ancestorNames(type).stream().map(hierarchy::find).flatMap(Optional::stream).toList();
  }

  private static List<FieldRef> fields(final JvmClass type) {
    return type.fields().stream().map(FactTypeMembers::fieldRef).toList();
  }

  static FieldRef fieldRef(final JvmField field) {
    return new FieldRef(
        field.name(),
        FactRefs.erased(field.type(), Map.of()).orElse(null),
        field.isStatic(),
        FactRefs.displayAll(field.annotations()));
  }

  /**
   * Every supertype above {@code type}, the direct ones first, each once, with {@code Object}
   * always among them.
   */
  List<TypeRef> ancestors(final JvmClass type) {
    return ancestorNames(type).stream().map(name -> TypeRef.of(FactRefs.display(name))).toList();
  }

  private Set<String> ancestorNames(final JvmClass type) {
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
    return seen;
  }
}
