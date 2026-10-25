package dev.ward.domain.project;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The Node major versions Ward supports, and the warning for a project that declares an older one.
 */
public final class NodeSupport {
  /** The lowest Node major version Ward supports. */
  public static final int MINIMUM_MAJOR = 22;

  private static final Pattern MAJOR = Pattern.compile("\\d{1,9}");

  private NodeSupport() {}

  /**
   * Returns the warning for a non-Bun project whose target Node major version is below {@link
   * #MINIMUM_MAJOR}, or empty when the version is supported or not a number.
   */
  public static Optional<String> warning(final Project project) {
    final String version = project.languageVersion();
    if (project.buildTool() == BuildTool.BUN || !MAJOR.matcher(version).matches()) {
      return Optional.empty();
    }
    final int major = Integer.parseInt(version);
    if (major >= MINIMUM_MAJOR) {
      return Optional.empty();
    }
    return Optional.of(
        "Ward supports Node 22 and 24; the project uses Node "
            + major
            + ", and Ward may not work with it.");
  }
}
