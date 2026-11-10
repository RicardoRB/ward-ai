package dev.ward.analysis.domain.types;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps annotation qualified names from JSpecify, JSR-305, the Checker Framework, AndroidX,
 * JetBrains, Eclipse, FindBugs and Spring to one {@link Nullness}, and applies the default scopes
 * ({@code @NullMarked}, {@code @ParametersAreNonnullByDefault} and the like).
 */
public final class NullnessAnnotations {
  /** Marks an expression whose Kotlin type is non-null, as the native Kotlin typer writes it. */
  public static final String KOTLIN_NON_NULL = "dev.ward.kotlin.NonNull";

  /** Marks an expression whose Kotlin type is nullable, as the native Kotlin typer writes it. */
  public static final String KOTLIN_NULLABLE = "dev.ward.kotlin.Nullable";

  private static final Logger LOGGER = Logger.getLogger(NullnessAnnotations.class.getName());

  private static final Map<String, Nullness> DECLARATIONS =
      Map.ofEntries(
          Map.entry(KOTLIN_NON_NULL, Nullness.NON_NULL),
          Map.entry(KOTLIN_NULLABLE, Nullness.NULLABLE),
          Map.entry("org.jspecify.annotations.Nullable", Nullness.NULLABLE),
          Map.entry("org.jspecify.annotations.NonNull", Nullness.NON_NULL),
          Map.entry("javax.annotation.Nullable", Nullness.NULLABLE),
          Map.entry("javax.annotation.CheckForNull", Nullness.NULLABLE),
          Map.entry("javax.annotation.Nonnull", Nullness.NON_NULL),
          Map.entry("org.checkerframework.checker.nullness.qual.Nullable", Nullness.NULLABLE),
          Map.entry("org.checkerframework.checker.nullness.qual.NonNull", Nullness.NON_NULL),
          Map.entry("androidx.annotation.Nullable", Nullness.NULLABLE),
          Map.entry("androidx.annotation.NonNull", Nullness.NON_NULL),
          Map.entry("android.support.annotation.Nullable", Nullness.NULLABLE),
          Map.entry("android.support.annotation.NonNull", Nullness.NON_NULL),
          Map.entry("org.jetbrains.annotations.Nullable", Nullness.NULLABLE),
          Map.entry("org.jetbrains.annotations.NotNull", Nullness.NON_NULL),
          Map.entry("org.eclipse.jdt.annotation.Nullable", Nullness.NULLABLE),
          Map.entry("org.eclipse.jdt.annotation.NonNull", Nullness.NON_NULL),
          Map.entry("edu.umd.cs.findbugs.annotations.Nullable", Nullness.NULLABLE),
          Map.entry("edu.umd.cs.findbugs.annotations.NonNull", Nullness.NON_NULL));

  private static final Map<String, Nullness> SCOPES =
      Map.of(
          "org.jspecify.annotations.NullMarked", Nullness.NON_NULL,
          "org.jspecify.annotations.NullUnmarked", Nullness.UNKNOWN,
          "javax.annotation.ParametersAreNonnullByDefault", Nullness.NON_NULL,
          "org.eclipse.jdt.annotation.NonNullByDefault", Nullness.NON_NULL,
          "org.springframework.lang.NonNullApi", Nullness.NON_NULL,
          "org.springframework.lang.NonNullFields", Nullness.NON_NULL);

  private NullnessAnnotations() {}

  /**
   * Returns the nullness a declaration's own annotations give, else the one its nearest default
   * scope gives, else {@link Nullness#UNKNOWN}.
   *
   * @param own the declaration's annotation names
   * @param scopes the annotation names of its enclosing declarations, nearest first
   */
  public static Nullness of(final List<String> own, final List<String> scopes) {
    for (final String name : own) {
      final Nullness declared = DECLARATIONS.get(name);
      if (declared != null) {
        return declared;
      }
    }
    for (final String name : scopes) {
      final Nullness scoped = SCOPES.get(name);
      if (scoped != null) {
        if (LOGGER.isLoggable(Level.FINE)) {
          LOGGER.fine("event=nullness-scope scope=" + name);
        }
        return scoped;
      }
    }
    return Nullness.UNKNOWN;
  }
}
