package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.TypeComponent;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;

/** Tells whether a value of one class or interface type can be cast to another. */
final class TypeCasts {

  private TypeCasts() {}

  /** Tells whether a value of one class or interface type can be cast to the other. */
  static boolean castable(final TypeComponent from, final TypeComponent to) {
    final TypeRef source = from.type();
    final TypeRef target = to.type();
    return source.name().equals(target.name())
        || related(from, target)
        || related(to, source)
        || bothOpen(from.shape(), to.shape())
        || openAndNotFinal(from.shape(), to.shape());
  }

  private static boolean related(final TypeComponent type, final TypeRef other) {
    return type.members().ancestors().stream().anyMatch(a -> a.name().equals(other.name()));
  }

  private static boolean bothOpen(final TypeShape one, final TypeShape other) {
    return interfaceLike(one) && interfaceLike(other);
  }

  /** An interface can be cast to any class that is not final, and the other way round. */
  private static boolean openAndNotFinal(final TypeShape one, final TypeShape other) {
    return interfaceLike(one) && !finalClass(other) || interfaceLike(other) && !finalClass(one);
  }

  private static boolean interfaceLike(final TypeShape shape) {
    return shape.kind() == TypeShape.Kind.INTERFACE || shape.kind() == TypeShape.Kind.ANNOTATION;
  }

  private static boolean finalClass(final TypeShape shape) {
    return shape.isFinal()
        || shape.kind() == TypeShape.Kind.ENUM
        || shape.kind() == TypeShape.Kind.RECORD;
  }
}
