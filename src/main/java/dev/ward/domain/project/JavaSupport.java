package dev.ward.domain.project;

import java.util.Optional;

/** The JDK feature versions Ward supports, and the warning for a build that runs on another. */
public final class JavaSupport {
  /** The lowest JDK Ward supports. */
  public static final int MINIMUM_JDK = 8;

  /** The highest JDK Ward supports. */
  public static final int MAXIMUM_JDK = 25;

  private JavaSupport() {}

  /**
   * Returns the JDK to choose tool versions for: {@code jdk}, or {@link #MAXIMUM_JDK} for a newer
   * JDK, which is configured as the newest one Ward supports.
   */
  public static int selectionJdk(final int jdk) {
    return Math.min(jdk, MAXIMUM_JDK);
  }

  /** Returns the warning for a runtime JDK outside 8 to 25, or empty when it is supported. */
  public static Optional<String> warning(final int jdk) {
    if (jdk > MAXIMUM_JDK) {
      return Optional.of(
          "Ward has not been tested on JDK "
              + jdk
              + " and does not support it; it is configured as JDK 25.");
    }
    if (jdk < MINIMUM_JDK) {
      return Optional.of(
          "Ward supports Java 8 to 25; the project builds on JDK "
              + jdk
              + ", and Ward may not work with it.");
    }
    return Optional.empty();
  }
}
