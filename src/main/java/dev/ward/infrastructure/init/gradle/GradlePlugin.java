package dev.ward.infrastructure.init.gradle;

/**
 * A plugin init adds, with the gate that selects it. A module-placed plugin goes into each Kotlin
 * module script; a versioned one then needs {@code apply false} in the root when the root does not
 * apply it.
 */
record GradlePlugin(String gate, String id, boolean versioned, boolean module) {
  /**
   * The declaration for the Groovy or the Kotlin DSL, with a version and {@code apply false} when
   * set.
   */
  String declaration(final boolean kotlin, final String version, final boolean applyFalse) {
    final String base = kotlin ? "id(\"" + id + "\")" : "id '" + id + "'";
    final String pinned = version.isBlank() ? "" : " version " + quote(kotlin, version);
    return base + pinned + (applyFalse ? " apply false" : "");
  }

  private static String quote(final boolean kotlin, final String value) {
    return kotlin ? "\"" + value + "\"" : "'" + value + "'";
  }
}
