package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.Map;
import java.util.Optional;

/** Classifies a known TypeScript type into the {@link ValueKind} the restrict-* rules use. */
final class ValueKinds {

  private static final String ANY = "any";
  private static final String UNKNOWN = "unknown";
  private static final String NUMBER = "number";
  private static final String STRING = "string";
  private static final String BIGINT = "bigint";
  private static final String REGEXP = "RegExp";
  private static final Map<String, ValueKind> NAMED =
      Map.ofEntries(
          Map.entry("boolean", ValueKind.BOOLEAN),
          Map.entry("true", ValueKind.BOOLEAN),
          Map.entry("false", ValueKind.BOOLEAN),
          Map.entry("null", ValueKind.NULLISH),
          Map.entry("undefined", ValueKind.NULLISH),
          Map.entry("void", ValueKind.NULLISH),
          Map.entry("never", ValueKind.NEVER),
          Map.entry(REGEXP, ValueKind.REGEXP),
          Map.entry(NUMBER, ValueKind.NUMBER),
          Map.entry(BIGINT, ValueKind.BIGINT),
          Map.entry(STRING, ValueKind.STRING));

  private ValueKinds() {}

  /** Returns the kind of a type that the facts answered. */
  static ValueKind of(final TypeRef type) {
    final String name = type.name();
    if (type.arrayDepth() == 0 && ANY.equals(name)) {
      return ValueKind.ANY;
    }
    if (type.arrayDepth() > 0 || isArray(name)) {
      return ValueKind.ARRAY;
    }
    return Optional.ofNullable(NAMED.get(name))
        .or(() -> byPattern(name, type))
        .orElse(ValueKind.OTHER);
  }

  /** Classifies a literal type by its text, or an object type by its primitiveness. */
  private static Optional<ValueKind> byPattern(final String name, final TypeRef type) {
    if (name.matches("-?[0-9].*")) {
      return Optional.of(ValueKind.NUMBER);
    }
    if (name.startsWith("\"") || name.startsWith("'")) {
      return Optional.of(ValueKind.STRING);
    }
    return isObject(type) ? Optional.of(ValueKind.OBJECT) : Optional.empty();
  }

  private static boolean isArray(final String name) {
    return "Array".equals(name)
        || "ReadonlyArray".equals(name)
        || name.startsWith("Array<")
        || name.startsWith("ReadonlyArray<");
  }

  /** An object type is one that is not a primitive and not unknown. */
  private static boolean isObject(final TypeRef type) {
    return !UNKNOWN.equals(type.name()) && !TypedCheck.isPrimitive(type);
  }
}
