package dev.ward.domain.project;

import java.util.Optional;

/** The JDK feature versions Ward supports, and the warning for a build that runs on another. */
public final class JavaSupport {
  /** The lowest JDK Ward supports. */
  public static final int MINIMUM_JDK = 8;

  /** The highest JDK Ward supports. */
  public static final int MAXIMUM_JDK = 25;

  private JavaSupport() {}

  /** Returns the warning for a runtime JDK outside 8 to 25, or empty when it is supported. */
  public static Optional<String> warning(final int jdk) {
    if (jdk >= MINIMUM_JDK && jdk <= MAXIMUM_JDK) {
      return Optional.empty();
    }
    return Optional.of(
        "Ward supports Java 8 to 25; the project builds on JDK "
            + jdk
            + ", and Ward may not work with it.");
  }
}
