package dev.ward.domain.project;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Xcode versions Ward supports, and the error for an older selected Xcode. */
public final class SwiftSupport {
  /** The lowest Xcode major version Ward supports. */
  public static final int MINIMUM_XCODE_MAJOR = 26;

  /** The lowest Xcode minor version Ward supports within {@link #MINIMUM_XCODE_MAJOR}. */
  public static final int MINIMUM_XCODE_MINOR = 0;

  private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d{1,9})\\.(\\d{1,9}).*");

  private SwiftSupport() {}

  /**
   * Returns the error for an Xcode {@code version} below {@link #MINIMUM_XCODE_MAJOR}.{@link
   * #MINIMUM_XCODE_MINOR}, or empty when it is supported or not a {@code major.minor} number.
   */
  public static Optional<String> xcodeIncompatibility(final String version) {
    final Matcher matcher = MAJOR_MINOR.matcher(version);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    final int major = Integer.parseInt(matcher.group(1));
    final int minor = Integer.parseInt(matcher.group(2));
    if (!isBelowMinimum(major, minor)) {
      return Optional.empty();
    }
    return Optional.of(
        "Xcode "
            + version
            + " is INCOMPATIBLE; Ward needs Xcode "
            + MINIMUM_XCODE_MAJOR
            + "."
            + MINIMUM_XCODE_MINOR
            + " or newer.");
  }

  private static boolean isBelowMinimum(final int major, final int minor) {
    return major < MINIMUM_XCODE_MAJOR
        || major == MINIMUM_XCODE_MAJOR && minor < MINIMUM_XCODE_MINOR;
  }
}
