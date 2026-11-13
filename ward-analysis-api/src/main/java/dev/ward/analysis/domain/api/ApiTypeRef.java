package dev.ward.analysis.domain.api;

import java.util.Objects;

/**
 * A type as an API signature writes it.
 *
 * @param text the type for messages, with qualified names and type arguments
 * @param key the form two types are compared by: qualified names and arguments, with type variables
 *     replaced by their position so that a renamed variable is the same type
 * @param erasure the qualified name without type arguments, with {@code []} for each array level
 * @param nullable whether a language with null safety allows {@code null}; false elsewhere
 */
public record ApiTypeRef(String text, String key, String erasure, boolean nullable) {
  /** The key of a type that a declaration leaves to the compiler to infer, so it is unknown. */
  public static final String INFERRED = "<inferred>";

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public ApiTypeRef {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(erasure, "erasure");
  }

  /** Returns whether the declaration wrote no type, so the type is unknown. */
  public boolean isInferred() {
    return INFERRED.equals(key);
  }

  /** Returns a type with no type arguments and no type variables, that is not nullable. */
  public static ApiTypeRef of(final String qualifiedName) {
    return new ApiTypeRef(qualifiedName, qualifiedName, qualifiedName, false);
  }
}
