package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts the resolver's types and members into the facts that rules read. */
final class FactRefs {
  private final Hierarchy hierarchy;

  FactRefs(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
  }

  /** The display name of a class: nested classes are separated by dots. */
  static String display(final String binary) {
    return binary.replace('$', '.');
  }

  /** Returns the reference to {@code type}; empty for the null type and unresolved wildcards. */
  Optional<TypeRef> ref(final JType type, final Map<String, JType> bounds) {
    return switch (type) {
      case JType.Primitive primitive ->
          Optional.of(new TypeRef(primitive.name(), List.of(), 0, true));
      case JType.ClassType named ->
          Types.isNull(named) ? Optional.empty() : Optional.of(named(named, bounds));
      case JType.ArrayType array -> ref(array.component(), bounds).map(FactRefs::deeper);
      case JType.TypeVariable variable ->
          ref(bounds.getOrDefault(variable.name(), JType.ClassType.of(Types.OBJECT)), Map.of());
      case JType.Wildcard wildcard -> Optional.empty();
    };
  }

  private static TypeRef deeper(final TypeRef base) {
    return new TypeRef(base.name(), base.arguments(), base.arrayDepth() + 1, base.primitive());
  }

  private TypeRef named(final JType.ClassType type, final Map<String, JType> bounds) {
    final List<TypeRef> arguments = new ArrayList<>();
    for (final JType argument : type.arguments()) {
      final Optional<TypeRef> resolved = ref(argument, bounds);
      if (resolved.isEmpty()) {
        return TypeRef.of(display(type.name()));
      }
      arguments.add(resolved.get());
    }
    return new TypeRef(display(type.name()), arguments, 0, false);
  }

  /** Returns the reference to the erasure of {@code type}: no type arguments. */
  Optional<TypeRef> erased(final JType type, final Map<String, JType> bounds) {
    return switch (type) {
      case JType.ClassType named -> Optional.of(TypeRef.of(display(named.name())));
      case JType.ArrayType array -> erased(array.component(), bounds).map(FactRefs::deeper);
      default -> ref(type, bounds);
    };
  }

  /** Returns the bounds of the type parameters of a method and its owner. */
  static Map<String, JType> bounds(final JvmClass owner, final JvmMethod method) {
    final Map<String, JType> bounds = new java.util.HashMap<>();
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
  Optional<CallTarget> target(final JvmClass owner, final JvmMethod method) {
    final Map<String, JType> bounds = bounds(owner, method);
    final List<TypeRef> parameters = new ArrayList<>();
    for (final JType parameter : method.parameters()) {
      final Optional<TypeRef> resolved = erased(parameter, bounds);
      if (resolved.isEmpty()) {
        return Optional.empty();
      }
      parameters.add(resolved.get());
    }
    return Optional.of(
        new CallTarget(
            TypeRef.of(display(owner.name())),
            method.name(),
            parameters,
            ref(method.returnType(), bounds).orElse(null),
            method.isStatic(),
            method.isVarargs()));
  }

  /** Returns what the class is and what it extends. */
  TypeShape shape(final JvmClass type) {
    final List<TypeRef> supertypes = new ArrayList<>();
    final Map<String, JType> bounds = new java.util.HashMap<>();
    type.typeParameters().forEach(parameter -> bounds.put(parameter.name(), bound(parameter)));
    if (type.superclass().isEmpty() && !Types.OBJECT.equals(type.name())) {
      supertypes.add(TypeRef.of(Types.OBJECT));
    }
    for (final JType.ClassType supertype : type.supertypes()) {
      ref(supertype, bounds).ifPresent(supertypes::add);
    }
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
    final List<TypeRef> checked = new ArrayList<>();
    for (final JType type : thrown) {
      if (type instanceof JType.ClassType named
          && hierarchy.find(named.name()).isPresent()
          && !hierarchy.isSubclass(named.name(), "java.lang.RuntimeException")
          && !hierarchy.isSubclass(named.name(), "java.lang.Error")) {
        checked.add(TypeRef.of(display(named.name())));
      }
    }
    return checked;
  }

  /** Returns the methods of the supertypes of {@code owner} that {@code method} overrides. */
  List<CallTarget> overridden(final JvmClass owner, final JvmMethod method) {
    final List<CallTarget> found = new ArrayList<>();
    final java.util.Deque<JType.ClassType> pending =
        new java.util.ArrayDeque<>(hierarchy.directSupertypes(selfOf(owner)));
    final java.util.Set<String> seen = new java.util.HashSet<>();
    while (!pending.isEmpty()) {
      final JType.ClassType next = pending.poll();
      final Optional<JvmClass> parent = hierarchy.find(next.name());
      if (parent.isEmpty() || !seen.add(next.name())) {
        continue;
      }
      final Map<String, JType> bound = hierarchy.bindings(parent.get(), next);
      for (final JvmMethod candidate : parent.get().methods()) {
        if (overrides(method, candidate, bound)) {
          target(parent.get(), candidate).ifPresent(found::add);
        }
      }
      pending.addAll(hierarchy.directSupertypes(next));
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

  private boolean overrides(
      final JvmMethod method, final JvmMethod candidate, final Map<String, JType> bound) {
    if (!candidate.name().equals(method.name())
        || candidate.isConstructor()
        || candidate.isStatic()
        || (candidate.access() & Flags.PRIVATE) != 0
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
