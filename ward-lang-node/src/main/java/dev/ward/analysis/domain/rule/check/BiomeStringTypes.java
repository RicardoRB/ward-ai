package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Optional;

/** Tells whether a value's type gives it only the default object stringification. */
final class BiomeStringTypes {

  private static final String OBJECT = "{}";
  private static final String OBJECT_CLASS = "Object";
  private static final String TO_STRING = "toString";

  private BiomeStringTypes() {}

  /** The value has a plain object type, or a class type that declares no toString. */
  static boolean defaultObject(final SyntaxNode value, final UnitTypeFacts types) {
    final boolean plainObject =
        types
            .typeOf(value.span())
            .map(type -> type.arrayDepth() == 0 && isObjectName(type.name()))
            .orElse(false);
    return plainObject || isClassWithoutToString(value.span(), types);
  }

  /** The receiver is an array whose element type is a plain object. */
  static boolean objectArray(final SyntaxNode array, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(array.span());
    return type.filter(t -> t.arrayDepth() > 0 && OBJECT.equals(t.name())).isPresent();
  }

  private static boolean isObjectName(final String name) {
    return OBJECT.equals(name) || OBJECT_CLASS.equals(name);
  }

  private static boolean isClassWithoutToString(final Span span, final UnitTypeFacts types) {
    final boolean isClass =
        types.shapeOf(span).map(shape -> shape.kind() == TypeShape.Kind.CLASS).orElse(false);
    return isClass && types.membersOf(span).map(BiomeStringTypes::lacksToString).orElse(false);
  }

  /**
   * The class declares no toString, neither itself nor through an inherited member, and every base
   * it lists is Object. A base that is not Object may declare toString, so it stays silent.
   */
  private static boolean lacksToString(final TypeMembers members) {
    return members.methods().stream().noneMatch(BiomeStringTypes::isToString)
        && members.inheritedMethods().stream().noneMatch(BiomeStringTypes::isToString)
        && members.ancestors().stream().allMatch(BiomeStringTypes::isPlainObject);
  }

  private static boolean isPlainObject(final TypeRef type) {
    return type.arrayDepth() == 0 && isObjectName(type.name());
  }

  private static boolean isToString(final CallTarget method) {
    return TO_STRING.equals(method.name());
  }
}
