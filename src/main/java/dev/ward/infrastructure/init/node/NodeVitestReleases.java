package dev.ward.infrastructure.init.node;

import java.util.List;
import java.util.Optional;

/**
 * The Vitest releases init pins, each with the Vite range its npm dependencies declare. A project's
 * Vite picks the release, and a Vite outside every range has no pin.
 */
final class NodeVitestReleases {
  private static final String VITE_4_0 = "^6.0.0 || ^7.0.0";
  private static final String VITE_4_1 = "^6.0.0 || ^7.0.0 || ^8.0.0";

  /** The releases, newest first, so the first one whose Vite range includes a Vite is its pin. */
  private static final List<Release> RELEASES =
      List.of(
          new Release("4.1.11", VITE_4_1),
          new Release(NodePackageCatalog.VITEST_VERSION, VITE_4_0));

  /**
   * One pinned Vitest release.
   *
   * @param version the Vitest version, which {@code @vitest/coverage-v8} shares
   * @param vite the Vite range the release declares
   */
  record Release(String version, String vite) {}

  private NodeVitestReleases() {}

  /** Returns the newest pinned release whose Vite range includes {@code vite}, if any. */
  static Optional<Release> forVite(final String vite) {
    return RELEASES.stream()
        .filter(release -> NodeVersionRange.matches(release.vite(), vite))
        .findFirst();
  }

  /** Returns the pinned release on the same minor as {@code version}, if any. */
  static Optional<Release> onMinor(final String version) {
    return RELEASES.stream()
        .filter(release -> minor(release.version()).equals(minor(version)))
        .findFirst();
  }

  /** Returns the Vite range of the newest pinned release, for a message. */
  static String newestViteRange() {
    return RELEASES.getFirst().vite();
  }

  private static String minor(final String version) {
    return version.substring(0, version.lastIndexOf('.'));
  }
}
