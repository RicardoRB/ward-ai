package dev.ward.domain.project;

/** The application framework detected in a project. */
public enum Framework {
  SPRING_BOOT("Spring Boot"),
  VUE("Vue"),
  SVELTE("Svelte"),
  ASTRO("Astro"),
  ANGULAR("Angular"),
  NONE("None");

  private final String displayName;

  Framework(final String displayName) {
    this.displayName = displayName;
  }

  /** Returns the name shown to users. */
  public String displayName() {
    return displayName;
  }
}
