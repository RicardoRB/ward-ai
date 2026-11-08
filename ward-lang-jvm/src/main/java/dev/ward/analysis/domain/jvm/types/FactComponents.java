package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.TypeComponent;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Optional;

/** Reads the components of a type for the {@code MEMBERS} fact: its type arguments or element. */
final class FactComponents {

  private static final List<TypeRef> ARRAY_SUPERTYPES =
      List.of(
          TypeRef.of("java.lang.Object"),
          TypeRef.of("java.lang.Cloneable"),
          TypeRef.of("java.io.Serializable"));

  private final Hierarchy hierarchy;
  private final FactTypeMembers owner;

  FactComponents(final Hierarchy hierarchy, final FactTypeMembers owner) {
    this.hierarchy = hierarchy;
    this.owner = owner;
  }

  /** The members of an array type: nothing of its own, only its element type as a component. */
  Optional<TypeMembers> arrayOnly(final Typed typed, final Optional<DeclInfo> declaration) {
    return declaration.isEmpty()
            && typed.type().filter(JType.ArrayType.class::isInstance).isPresent()
        ? Optional.of(new TypeMembers(List.of(), List.of(), ARRAY_SUPERTYPES, List.of()))
        : Optional.empty();
  }

  TypeMembers add(
      final TypeMembers members, final Typed typed, final Optional<DeclInfo> declaration) {
    final List<JType> parts =
        declaration.isPresent()
            ? List.of()
            : typed.type().map(FactComponents::partsOf).orElse(List.of());
    if (parts.isEmpty()) {
      return members;
    }
    return new TypeMembers(
        members.methods(),
        members.fields(),
        members.ancestors(),
        members.annotations(),
        members.inheritedMethods(),
        members.inheritedFields(),
        members.annotationValues(),
        parts.stream().map(this::component).toList());
  }

  /** The type arguments of a class type, or the element type of an array type. */
  private static List<JType> partsOf(final JType type) {
    if (type instanceof JType.ClassType classType) {
      return classType.arguments();
    }
    return type instanceof JType.ArrayType array ? List.of(array.component()) : List.of();
  }

  private TypeComponent component(final JType part) {
    if (part instanceof JType.ClassType classType) {
      final Optional<JvmClass> found = hierarchy.find(classType.name());
      if (found.isPresent()) {
        return new TypeComponent(
            TypeRef.of(FactRefs.display(classType.name())),
            FactMembers.shape(found.get()),
            new TypeMembers(List.of(), List.of(), owner.ancestors(found.get()), List.of()));
      }
    }
    return TypeComponent.UNKNOWN;
  }
}
