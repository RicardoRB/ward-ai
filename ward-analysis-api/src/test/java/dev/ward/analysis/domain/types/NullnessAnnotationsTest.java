package dev.ward.analysis.domain.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NullnessAnnotationsTest {
  private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";

  @Test
  void aNullMarkedScopeMakesAnUnannotatedDeclarationNonNull() {
    assertThat(NullnessAnnotations.of(List.of(), List.of(NULL_MARKED)))
        .isEqualTo(Nullness.NON_NULL);
  }

  @Test
  void parametersAreNonnullByDefaultIsAScopeToo() {
    assertThat(
            NullnessAnnotations.of(
                List.of(), List.of("javax.annotation.ParametersAreNonnullByDefault")))
        .isEqualTo(Nullness.NON_NULL);
  }

  @Test
  void anOwnAnnotationBeatsTheScope() {
    assertThat(
            NullnessAnnotations.of(
                List.of("org.jspecify.annotations.Nullable"), List.of(NULL_MARKED)))
        .isEqualTo(Nullness.NULLABLE);
  }

  @Test
  void theNearestScopeWins() {
    assertThat(
            NullnessAnnotations.of(
                List.of(), List.of("org.jspecify.annotations.NullUnmarked", NULL_MARKED)))
        .isEqualTo(Nullness.UNKNOWN);
  }

  @Test
  void nothingAnnotatedIsUnknown() {
    assertThat(NullnessAnnotations.of(List.of("java.lang.Override"), List.of()))
        .isEqualTo(Nullness.UNKNOWN);
  }

  @ParameterizedTest
  @CsvSource({
    "org.jspecify.annotations.Nullable,NULLABLE",
    "org.jspecify.annotations.NonNull,NON_NULL",
    "javax.annotation.Nullable,NULLABLE",
    "javax.annotation.CheckForNull,NULLABLE",
    "javax.annotation.Nonnull,NON_NULL",
    "org.checkerframework.checker.nullness.qual.Nullable,NULLABLE",
    "org.checkerframework.checker.nullness.qual.NonNull,NON_NULL",
    "androidx.annotation.Nullable,NULLABLE",
    "androidx.annotation.NonNull,NON_NULL",
    "org.jetbrains.annotations.Nullable,NULLABLE",
    "org.jetbrains.annotations.NotNull,NON_NULL",
    "org.eclipse.jdt.annotation.Nullable,NULLABLE",
    "org.eclipse.jdt.annotation.NonNull,NON_NULL"
  })
  void mapsEachFamilyOfAnnotations(final String annotation, final Nullness expected) {
    assertThat(NullnessAnnotations.of(List.of(annotation), List.of())).isEqualTo(expected);
  }
}
