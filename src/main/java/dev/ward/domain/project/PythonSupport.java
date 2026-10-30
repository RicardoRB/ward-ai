package dev.ward.domain.project;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Python versions Ward supports, and the error for a project whose target version is older. */
public final class PythonSupport {
  /** The lowest Python major version Ward supports. */
  public static final int MINIMUM_MAJOR = 3;

  /** The lowest Python minor version Ward supports within {@link #MINIMUM_MAJOR}. */
  public static final int MINIMUM_MINOR = 11;

  /** The highest Python minor version Ward supports within {@link #MINIMUM_MAJOR}. */
  public static final int MAXIMUM_MINOR = 15;

  private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})");

  private PythonSupport() {}

  /**
   * Returns the error for a project whose target {@code major.minor} version is below {@link
   * #MINIMUM_MAJOR}.{@link #MINIMUM_MINOR}, or empty when the version is supported or not a {@code
   * major.minor} number.
   */
  public static Optional<String> incompatibility(final Project project) {
    final Matcher matcher = MAJOR_MINOR.matcher(project.languageVersion());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    final int major = Integer.parseInt(matcher.group(1));
    final int minor = Integer.parseInt(matcher.group(2));
    if (!isBelowMinimum(major, minor)) {
      return Optional.empty();
    }
    return Optional.of(
        "Python "
            + project.languageVersion()
            + " is INCOMPATIBLE; Ward supports Python "
            + MINIMUM_MAJOR
            + "."
            + MINIMUM_MINOR
            + " to "
            + MINIMUM_MAJOR
            + "."
            + MAXIMUM_MINOR
            + ".");
  }

  private static boolean isBelowMinimum(final int major, final int minor) {
    return major < MINIMUM_MAJOR || major == MINIMUM_MAJOR && minor < MINIMUM_MINOR;
  }
}
