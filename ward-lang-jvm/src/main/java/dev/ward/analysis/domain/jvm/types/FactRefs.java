package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts the resolver's types into the type references that rules read. */
final class FactRefs {
  private FactRefs() {}

  /** The display name of a class: nested classes are separated by dots. */
  static String display(final String binary) {
    return binary.replace('$', '.');
  }

  /** Returns the reference to {@code type}; empty for the null type and unresolved wildcards. */
  static Optional<TypeRef> ref(final JType type, final Map<String, JType> bounds) {
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

  private static TypeRef named(final JType.ClassType type, final Map<String, JType> bounds) {
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
  static Optional<TypeRef> erased(final JType type, final Map<String, JType> bounds) {
    return switch (type) {
      case JType.ClassType named -> Optional.of(TypeRef.of(display(named.name())));
      case JType.ArrayType array -> erased(array.component(), bounds).map(FactRefs::deeper);
      default -> ref(type, bounds);
    };
  }
}
