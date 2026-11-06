package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Map;

/** Operations on {@link JType}s that need no class lookup. */
final class Types {
  static final String OBJECT = "java.lang.Object";
  static final String STRING = "java.lang.String";
  static final String NULL = "<null>";
  private static final List<String> WIDENING =
      List.of("byte", "short", "int", "long", "float", "double");
  private static final Map<String, String> BOXES =
      Map.of(
          "boolean", "java.lang.Boolean",
          "byte", "java.lang.Byte",
          "short", "java.lang.Short",
          "char", "java.lang.Character",
          "int", "java.lang.Integer",
          "long", "java.lang.Long",
          "float", "java.lang.Float",
          "double", "java.lang.Double");

  private Types() {}

  static boolean isPrimitive(final JType type) {
    return type instanceof JType.Primitive primitive && !"void".equals(primitive.name());
  }

  static boolean isNull(final JType type) {
    return type instanceof JType.ClassType named && NULL.equals(named.name());
  }

  /** Returns the class of a primitive's wrapper, or the type itself. */
  static JType boxed(final JType type) {
    if (type instanceof JType.Primitive primitive && BOXES.containsKey(primitive.name())) {
      return JType.ClassType.of(BOXES.get(primitive.name()));
    }
    return type;
  }

  /** Returns the primitive that {@code type} wraps, or the type itself. */
  static JType unboxed(final JType type) {
    if (type instanceof JType.ClassType named) {
      for (final Map.Entry<String, String> box : BOXES.entrySet()) {
        if (box.getValue().equals(named.name())) {
          return new JType.Primitive(box.getKey());
        }
      }
    }
    return type;
  }

  /** Returns whether primitive {@code from} widens to or equals primitive {@code to}. */
  static boolean widens(final String from, final String to) {
    if (from.equals(to)) {
      return true;
    }
    if ("char".equals(from)) {
      return WIDENING.indexOf(to) >= WIDENING.indexOf("int");
    }
    final int source = WIDENING.indexOf(from);
    final int target = WIDENING.indexOf(to);
    return source >= 0 && target >= 0 && source < target;
  }

  /** Returns the binary promotion of two numeric types, empty-handed as {@code null} otherwise. */
  static JType promote(final JType left, final JType right) {
    final JType first = unboxed(left);
    final JType second = unboxed(right);
    if (!(first instanceof JType.Primitive a) || !(second instanceof JType.Primitive b)) {
      return null;
    }
    for (final String wide : List.of("double", "float", "long")) {
      if (wide.equals(a.name()) || wide.equals(b.name())) {
        return new JType.Primitive(wide);
      }
    }
    return "boolean".equals(a.name()) || "boolean".equals(b.name()) ? null : new JType.Primitive("int");
  }

  /** Replaces the type variables named in {@code bindings}. */
  static JType substitute(final JType type, final Map<String, JType> bindings) {
    if (bindings.isEmpty()) {
      return type;
    }
    if (type instanceof JType.TypeVariable variable) {
      return bindings.getOrDefault(variable.name(), type);
    }
    if (type instanceof JType.ClassType named) {
      return new JType.ClassType(
          named.name(), named.arguments().stream().map(arg -> substitute(arg, bindings)).toList());
    }
    if (type instanceof JType.ArrayType array) {
      return new JType.ArrayType(substitute(array.component(), bindings));
    }
    if (type instanceof JType.Wildcard wildcard && wildcard.bound() != null) {
      return new JType.Wildcard(substitute(wildcard.bound(), bindings), wildcard.upper());
    }
    return type;
  }

  /** Returns whether {@code type} mentions a type variable that is not in {@code known}. */
  static boolean mentionsVariable(final JType type) {
    if (type instanceof JType.TypeVariable) {
      return true;
    }
    if (type instanceof JType.ClassType named) {
      return named.arguments().stream().anyMatch(Types::mentionsVariable);
    }
    if (type instanceof JType.ArrayType array) {
      return mentionsVariable(array.component());
    }
    return type instanceof JType.Wildcard wildcard && wildcard.bound() != null && mentionsVariable(wildcard.bound());
  }
}
