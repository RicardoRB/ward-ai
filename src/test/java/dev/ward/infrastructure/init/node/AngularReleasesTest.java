package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.PackageManifest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Angular release rows and their package-catalog pins stay in sync. */
class AngularReleasesTest {
  @Test
  void supportedMajorsExposeVerifiedCompatibilityData() {
    assertThat(AngularReleases.majors()).containsExactly("21", "22");
    assertThat(AngularReleases.forMajor("21"))
        .contains(
            new AngularRelease("21", "21.4.0", "^20.19.0 || ^22.12.0 || >=24.0.0", ">=5.9 <6.1"));
    assertThat(AngularReleases.forMajor("22"))
        .contains(
            new AngularRelease("22", "22.6.0", "^22.22.3 || ^24.15.0 || >=26.0.0", ">=6.0 <6.1"));
    assertThat(AngularReleases.forMajor("19")).isEmpty();
  }

  @Test
  void packageCatalogPinsMatchEveryReleaseRow() {
    assertThat(AngularReleases.majors()).allSatisfy(this::assertCatalogPinMatchesRelease);
  }

  private void assertCatalogPinMatchesRelease(final String major) {
    final AngularRelease release = AngularReleases.forMajor(major).orElseThrow();
    final PackageManifest manifest =
        new PackageManifest(
            Map.of(NodePackageCatalog.ANGULAR_CORE, "^" + major + ".0.0"), null, null);

    final NodePackageCatalog.Package angularEslint =
        NodePackageCatalog.angularEslint(manifest).orElseThrow();

    assertThat(angularEslint.version()).isEqualTo(release.angularEslint());
    assertThat(angularEslint.node()).isEqualTo(release.node());
  }
}
