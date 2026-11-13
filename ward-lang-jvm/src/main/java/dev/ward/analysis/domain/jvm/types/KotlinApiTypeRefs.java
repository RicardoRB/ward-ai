package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiTypeRef;

/**
 * The API types that no written Kotlin type gives: inferred, {@code Unit}, a class itself, arrays.
 */
final class KotlinApiTypeRefs {
  private static final String ARRAY = "[]";

  private KotlinApiTypeRefs() {}

  /** Returns the type of a declaration that writes none and has no body to read it from. */
  static ApiTypeRef inferred() {
    return new ApiTypeRef(ApiTypeRef.INFERRED, ApiTypeRef.INFERRED, ApiTypeRef.INFERRED, false);
  }

  /** Returns {@code void}, the type of {@code Unit}. */
  static ApiTypeRef unit() {
    return ApiTypeRef.of("void");
  }

  /** Returns the class {@code binary} itself, with its own type variables unnamed. */
  static ApiTypeRef named(final String binary) {
    return ApiTypeRefs.named(binary);
  }

  /** Returns the array of {@code element}, the type of a {@code vararg} parameter. */
  static ApiTypeRef array(final ApiTypeRef element) {
    return new ApiTypeRef(
        element.text() + ARRAY, element.key() + ARRAY, element.erasure() + ARRAY, false);
  }
}
