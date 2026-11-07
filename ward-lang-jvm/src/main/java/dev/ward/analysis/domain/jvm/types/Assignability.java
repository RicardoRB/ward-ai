package dev.ward.analysis.domain.jvm.types;

/** Decides whether a value of one type can be used where another type is expected. */
final class Assignability {
  private final Hierarchy hierarchy;

  Assignability(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
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
    final JType target =
        to instanceof JType.Wildcard wildcard && wildcard.bound() != null ? wildcard.bound() : to;
    if (target instanceof JType.ClassType named && Types.OBJECT.equals(named.name())) {
      return true;
    }
    if (from instanceof JType.ArrayType source) {
      return arrayAssignable(source, target);
    }
    return from instanceof JType.ClassType source
        && target instanceof JType.ClassType named
        && hierarchy.isSubclass(source.name(), named.name());
  }

  private boolean arrayAssignable(final JType.ArrayType source, final JType target) {
    if (target instanceof JType.ArrayType array) {
      return Types.isPrimitive(source.component())
          ? source.component().equals(array.component())
          : assignable(source.component(), array.component(), false);
    }
    return target instanceof JType.ClassType named
        && ("java.lang.Cloneable".equals(named.name())
            || "java.io.Serializable".equals(named.name()));
  }
}
