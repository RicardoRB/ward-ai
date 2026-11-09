package dev.ward.analysis.domain.node.lint;

import java.util.Map;
import java.util.Set;

/**
 * The options of restrict-template-expressions: which value kinds a template placeholder may take.
 *
 * @param allowAny whether an {@code any} placeholder is allowed
 * @param allowArray whether an array placeholder is allowed
 * @param allowBoolean whether a boolean placeholder is allowed
 * @param allowNullish whether a {@code null} or {@code undefined} placeholder is allowed
 * @param allowNumber whether a number or bigint placeholder is allowed
 * @param allowRegExp whether a regular expression placeholder is allowed
 * @param allowNever whether a {@code never} placeholder is allowed
 * @param allowedTypes the names of object types that are allowed, such as {@code Error}
 */
public record TemplatePolicy(
    boolean allowAny,
    boolean allowArray,
    boolean allowBoolean,
    boolean allowNullish,
    boolean allowNumber,
    boolean allowRegExp,
    boolean allowNever,
    Set<String> allowedTypes) {

  /** Copies the set of allowed type names. */
  public TemplatePolicy {
    allowedTypes = Set.copyOf(allowedTypes);
  }

  /** Reports whether a placeholder of this kind is allowed by the kind options. */
  boolean allows(final ValueKind kind) {
    return Map.of(
            ValueKind.ANY, allowAny,
            ValueKind.ARRAY, allowArray,
            ValueKind.BOOLEAN, allowBoolean,
            ValueKind.NULLISH, allowNullish,
            ValueKind.NUMBER, allowNumber,
            ValueKind.BIGINT, allowNumber,
            ValueKind.REGEXP, allowRegExp,
            ValueKind.NEVER, allowNever,
            ValueKind.OBJECT, false)
        .getOrDefault(kind, true);
  }
}
