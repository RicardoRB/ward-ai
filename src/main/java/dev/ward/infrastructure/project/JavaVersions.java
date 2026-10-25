package dev.ward.infrastructure.project;

/** Reads a Java feature version from the forms build tools print, such as {@code 1.8.0_412}. */
final class JavaVersions {
  private static final String LEGACY_PREFIX = "1.";

  private JavaVersions() {}

  /**
   * Returns the feature version of {@code value}: {@code 8} for {@code 1.8} or {@code 1.8.0_412},
   * {@code 17} for {@code 17.0.15}.
   *
   * @throws NumberFormatException if {@code value} does not start with a version number
   */
  static int feature(final String value) {
    final String normalized =
        value.startsWith(LEGACY_PREFIX) ? value.substring(LEGACY_PREFIX.length()) : value;
    return Integer.parseInt(normalized.replaceAll("[^0-9].*", ""));
  }
}
