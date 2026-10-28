package dev.ward.infrastructure.init;

import java.util.List;
import java.util.Optional;

/** The verified Angular release compatibility rows used by Node init. */
final class AngularReleases {
  private static final List<AngularRelease> RELEASES =
      List.of(
          new AngularRelease("21", "21.4.0", "^20.19.0 || ^22.12.0 || >=24.0.0", ">=5.9 <6.1"),
          new AngularRelease("22", "22.6.0", "^22.22.3 || ^24.15.0 || >=26.0.0", ">=6.0 <6.1"));

  private AngularReleases() {}

  /** Returns the compatibility row for {@code major}, if Ward supports that major. */
  static Optional<AngularRelease> forMajor(final String major) {
    return RELEASES.stream().filter(release -> release.major().equals(major)).findFirst();
  }

  /** Returns the supported Angular majors in stable order. */
  static List<String> majors() {
    return RELEASES.stream().map(AngularRelease::major).toList();
  }
}
