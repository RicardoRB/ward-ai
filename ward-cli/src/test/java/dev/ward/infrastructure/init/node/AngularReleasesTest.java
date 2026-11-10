package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The verified Angular release rows that Node init checks against. */
class AngularReleasesTest {
  @Test
  void supportedMajorsExposeVerifiedCompatibilityData() {
    assertThat(AngularReleases.majors()).containsExactly("21", "22");
    assertThat(AngularReleases.forMajor("21"))
        .contains(new AngularRelease("21", "^20.19.0 || ^22.12.0 || >=24.0.0", ">=5.9 <6.1"));
    assertThat(AngularReleases.forMajor("22"))
        .contains(new AngularRelease("22", "^22.22.3 || ^24.15.0 || >=26.0.0", ">=6.0 <6.1"));
    assertThat(AngularReleases.forMajor("19")).isEmpty();
  }
}
