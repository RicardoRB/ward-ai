package dev.ward.infrastructure.init.swift;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The Swift tools init pins, each with the Xcode range it is proven on. The SwiftLint release is
 * SwiftLintPlugins 0.65.1, the newest release at least 14 days old on 2026-10-31 (published
 * 2026-08-21); its {@code SwiftLintBinary} artifact bundle is checksummed in the release's {@code
 * Package.swift} as {@code c3a1d77647ca18c1b7e9be7dbc6cd4490d26422f28814b76370244ff61970869}.
 */
public final class SwiftToolCatalog {
  /** The SwiftLintPlugins repository the tools package depends on. */
  static final String SWIFTLINT_PLUGINS_URL = "https://github.com/SimplyDanny/SwiftLintPlugins";

  /** The exact SwiftLintPlugins version the tools package pins. */
  static final String SWIFTLINT_PLUGINS_VERSION = "0.65.1";

  /**
   * The Harmonize repository the architecture rules package depends on. Harmonize 1.3.0 is the
   * newest release at least 14 days old on 2026-11-01 (published 2026-09-16).
   */
  static final String HARMONIZE_URL = "https://github.com/perrystreetsoftware/Harmonize.git";

  /** The exact Harmonize version the architecture rules package pins. */
  static final String HARMONIZE_VERSION = "1.3.0";

  /** The tool that runs the SwiftLint gate, named as that gate's id. */
  static final String SWIFTLINT = "swiftlint";

  /** The tool that runs the Harmonize architecture gate, named as that gate's id. */
  static final String HARMONIZE = "harmonize";

  private static final Pattern XCODE = Pattern.compile("(\\d{1,9})\\.(\\d{1,9}).*");

  /**
   * One row per pinned version. The matrix proves the low and high Xcode of the range; a second row
   * appears only when a pinned tool drops an Xcode.
   */
  private static final List<SwiftToolRow> ROWS =
      List.of(
          new SwiftToolRow(SWIFTLINT, "SwiftLint", SWIFTLINT_PLUGINS_VERSION, "26.0", "28.0"),
          new SwiftToolRow(HARMONIZE, "Harmonize", HARMONIZE_VERSION, "26.0", "28.0"));

  private SwiftToolCatalog() {}

  /** Returns every row, so the matrix can check that a cell runs each one. */
  public static List<SwiftToolRow> rows() {
    return ROWS;
  }

  /** Returns the tools that have rows, in catalog order. */
  static List<String> tools() {
    return ROWS.stream().map(SwiftToolRow::tool).distinct().toList();
  }

  /** Returns the row of {@code tool} that covers {@code xcode}, or empty when none does. */
  static Optional<SwiftToolRow> forXcode(final String tool, final String xcode) {
    return ROWS.stream()
        .filter(row -> row.tool().equals(tool))
        .filter(row -> row.covers(xcode))
        .findFirst();
  }

  /**
   * Returns the plan note for a {@code tool} with no row for {@code xcode}: the Xcode ranges Ward
   * proves the tool on.
   */
  static String unprovenReason(final String tool, final String xcode) {
    final List<SwiftToolRow> rows = ROWS.stream().filter(row -> row.tool().equals(tool)).toList();
    final String ranges =
        rows.stream()
            .map(row -> "Xcode " + row.minimumXcode() + " up to " + row.maximumXcode())
            .collect(Collectors.joining(", "));
    return "no "
        + rows.getFirst().name()
        + " version is proven on Xcode "
        + xcode
        + "; Ward proves it on "
        + ranges;
  }

  /** Returns whether {@code version} is a {@code major.minor} Xcode version such as 26.6. */
  static boolean isXcodeVersion(final String version) {
    return XCODE.matcher(version).matches();
  }

  /**
   * Compares two Xcode versions by major, then minor. Both must be {@link #isXcodeVersion}
   * versions.
   */
  static int compareXcode(final String left, final String right) {
    final Matcher first = XCODE.matcher(left);
    final Matcher second = XCODE.matcher(right);
    if (!first.matches() || !second.matches()) {
      throw new IllegalArgumentException("Not an Xcode version: " + left + " or " + right);
    }
    final int major =
        Integer.compare(Integer.parseInt(first.group(1)), Integer.parseInt(second.group(1)));
    if (major != 0) {
      return major;
    }
    return Integer.compare(Integer.parseInt(first.group(2)), Integer.parseInt(second.group(2)));
  }

  /**
   * A pinned tool version and the Xcode range it is proven on, from {@code minimumXcode} up to, not
   * including, {@code maximumXcode}.
   *
   * @param tool the tool, which is also the gate id that runs it
   * @param name the tool's display name
   * @param version the exact version the tools package pins
   * @param minimumXcode the lowest Xcode proven, such as {@code 26.0}
   * @param maximumXcode the first Xcode not proven, such as {@code 28.0}
   */
  public record SwiftToolRow(
      String tool, String name, String version, String minimumXcode, String maximumXcode) {

    /** Returns whether {@code xcode} is in this row's range; an unreadable version is not. */
    public boolean covers(final String xcode) {
      return isXcodeVersion(xcode)
          && compareXcode(xcode, minimumXcode) >= 0
          && compareXcode(xcode, maximumXcode) < 0;
    }

    /** Returns whether this row and {@code other} are both proven on some Xcode. */
    boolean overlaps(final SwiftToolRow other) {
      return compareXcode(minimumXcode, other.maximumXcode) < 0
          && compareXcode(other.minimumXcode, maximumXcode) < 0;
    }
  }
}
