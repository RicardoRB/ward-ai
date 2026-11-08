package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts classes and methods into the shapes, call targets and overrides that rules read. */
final class FactMembers {
  private final Hierarchy hierarchy;

  FactMembers(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
  }

  /** Returns the bounds of the type parameters of a method and its owner. */
  static Map<String, JType> bounds(final JvmClass owner, final JvmMethod method) {
    final Map<String, JType> bounds = new HashMap<>();
    owner.typeParameters().forEach(parameter -> bounds.put(parameter.name(), bound(parameter)));
    method.typeParameters().forEach(parameter -> bounds.put(parameter.name(), bound(parameter)));
    return bounds;
  }

  private static JType bound(final TypeParameter parameter) {
    return parameter.bounds().isEmpty()
        ? JType.ClassType.of(Types.OBJECT)
        : parameter.bounds().get(0);
  }

  /** Returns the target a call resolves to, as declared. */
  static Optional<CallTarget> target(final JvmClass owner, final JvmMethod method) {
    final Map<String, JType> bounds = bounds(owner, method);
    final List<Optional<TypeRef>> parameters =
        method.parameters().stream().map(parameter -> FactRefs.erased(parameter, bounds)).toList();
    if (parameters.stream().anyMatch(Optional::isEmpty)) {
      return Optional.empty();
    }
    return Optional.of(
        new CallTarget(
            TypeRef.of(FactRefs.display(owner.name())),
            method.name(),
            parameters.stream().map(Optional::get).toList(),
            FactRefs.ref(method.returnType(), bounds).orElse(null),
            method.isStatic(),
            method.isVarargs(),
            FactRefs.displayAll(method.annotations()),
            method.parameterAnnotations().stream().map(FactRefs::displayAll).toList(),
            FactRefs.displayAll(owner.annotations()),
            method.compatibleWith(),
            method.typeParameters().stream().map(TypeParameter::name).toList(),
            owner.typeParameters().stream().map(TypeParameter::name).toList(),
            (method.access() & Flags.FINAL) != 0,
            (method.access() & Flags.ABSTRACT) != 0));
  }

  /** Returns what the class is and what it extends. */
  static TypeShape shape(final JvmClass type) {
    final List<TypeRef> supertypes = new ArrayList<>();
    final Map<String, JType> bounds = new HashMap<>();
    type.typeParameters().forEach(parameter -> bounds.put(parameter.name(), bound(parameter)));
    if (type.superclass().isEmpty() && !Types.OBJECT.equals(type.name())) {
      supertypes.add(TypeRef.of(Types.OBJECT));
    }
    type.supertypes()
        .forEach(supertype -> FactRefs.ref(supertype, bounds).ifPresent(supertypes::add));
    return new TypeShape(
        switch (type.kind()) {
          case INTERFACE -> TypeShape.Kind.INTERFACE;
          case ENUM -> TypeShape.Kind.ENUM;
          case RECORD -> TypeShape.Kind.RECORD;
          case ANNOTATION -> TypeShape.Kind.ANNOTATION;
          default -> TypeShape.Kind.CLASS;
        },
        type.isAbstract(),
        type.isFinal(),
        type.sealed(),
        supertypes);
  }

  /** Returns the checked exceptions among {@code thrown}. */
  List<TypeRef> checked(final List<JType> thrown) {
    return thrown.stream()
        .filter(JType.ClassType.class::isInstance)
        .map(type -> ((JType.ClassType) type).name())
        .filter(name -> hierarchy.find(name).isPresent())
        .filter(name -> !hierarchy.isSubclass(name, "java.lang.RuntimeException"))
        .filter(name -> !hierarchy.isSubclass(name, "java.lang.Error"))
        .map(name -> TypeRef.of(FactRefs.display(name)))
        .toList();
  }

  /** Returns the methods of the supertypes of {@code owner} that {@code method} overrides. */
  List<CallTarget> overridden(final JvmClass owner, final JvmMethod method) {
    final List<CallTarget> found = new ArrayList<>();
    final Deque<JType.ClassType> pending =
        new ArrayDeque<>(hierarchy.directSupertypes(selfOf(owner)));
    final Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      final Optional<JvmClass> parent = hierarchy.find(next.name());
      if (parent.isPresent() && seen.add(next.name())) {
        final Map<String, JType> bound = hierarchy.bindings(parent.get(), next);
        parent.get().methods().stream()
            .filter(candidate -> overrides(method, candidate, bound))
            .map(candidate -> target(parent.get(), candidate))
            .flatMap(Optional::stream)
            .forEach(found::add);
        pending.addAll(hierarchy.directSupertypes(next));
      }
    }
    return found;
  }

  private static JType.ClassType selfOf(final JvmClass owner) {
    return new JType.ClassType(
        owner.name(),
        owner.typeParameters().stream()
            .map(parameter -> (JType) new JType.TypeVariable(parameter.name()))
            .toList());
  }

  private static boolean overrides(
      final JvmMethod method, final JvmMethod candidate, final Map<String, JType> bound) {
    if (!candidate.name().equals(method.name())
        || candidate.isConstructor()
        || candidate.isStatic()
        || Flags.has(candidate.access(), Flags.PRIVATE)
        || candidate.parameters().size() != method.parameters().size()) {
      return false;
    }
    for (int index = 0; index < method.parameters().size(); index++) {
      final String mine = Types.erasure(method.parameters().get(index), Map.of());
      final String theirs =
          Types.erasure(Types.substitute(candidate.parameters().get(index), bound), Map.of());
      if (!mine.equals(theirs)) {
        return false;
      }
    }
    return true;
  }
}
