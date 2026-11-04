package dev.ward.domain.init;

import dev.ward.domain.project.Language;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Which checker enforces an architecture style: the generated external tool ({@code tool}, the
 * default) or the {@code ward} engine ({@code ward}), which reads the layer boundaries from the
 * {@code ward} gate.
 */
public enum ArchitectureEngine {
  /** The generated external tool, such as ArchUnit or dependency-cruiser. */
  TOOL,
  /** The {@code ward} engine, with {@code [[checks.ward.layers]]} boundaries. */
  WARD;

  /** The supported values, in the order they are listed to the user. */
  public static final String SUPPORTED = "tool, ward";

  /** Returns the engine named {@code id}, case-insensitively, or empty when none has that name. */
  public static Optional<ArchitectureEngine> parse(final String id) {
    return Arrays.stream(values())
        .filter(engine -> engine.name().equalsIgnoreCase(id.strip()))
        .findFirst();
  }

  /**
   * Returns the first of {@code languages} that the {@code ward} engine cannot resolve, or empty
   * when it resolves them all.
   */
  public static Optional<Language> unsupportedLanguage(final Set<Language> languages) {
    return languages.stream().sorted().filter(language -> !supports(language)).findFirst();
  }

  private static boolean supports(final Language language) {
    return ArchitectureLayers.ENGINE_LANGUAGES.stream()
        .anyMatch(engine -> engine.name().equals(language.name()));
  }

  /** Returns the value written on the command line. */
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}
