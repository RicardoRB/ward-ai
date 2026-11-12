package dev.ward.infrastructure.license.python;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonLicenseLocationTest {
  private static final String VERSION = "1.0";

  private static final String SITE = ".venv/lib/python3.12/site-packages/";

  @TempDir Path root;

  private LicenseLookup lookup(final String virtualEnv, final String name, final String version) {
    return new PythonLicenseMetadata(new BundledLicenseCatalog(), virtualEnv)
        .lookup(
            project(root, Language.PYTHON, BuildTool.UV),
            "",
            dependency(Ecosystem.PYPI, name, version));
  }

  private void metadata(final String directory, final String headers) throws IOException {
    write(root, SITE + directory + "/METADATA", "Metadata-Version: 2.4\n" + headers + "\n\nBody\n");
  }

  @Test
  void matchesNamesAsPep503Does() throws IOException {
    metadata("My_Package-1.0.dist-info", "Name: My_Package\nVersion: 1.0\nLicense-Expression: MIT");

    assertThat(lookup(null, "my.package", VERSION).present()).isTrue();
  }

  @Test
  void aDifferentVersionIsAMismatch() throws IOException {
    metadata("pkg-1.0.dist-info", "Name: pkg\nVersion: 1.0\nLicense-Expression: MIT");

    assertThat(lookup(null, "pkg", "2.0").reason())
        .isEqualTo(LicenseLookup.Reason.VERSION_MISMATCH);
  }

  @Test
  void noEnvironmentMeansNotInstalled() {
    final LicenseLookup result = lookup(null, "pkg", VERSION);

    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
    assertThat(result.detail()).contains("virtual environment");
  }

  @Test
  void readsTheEnvironmentNamedByVirtualEnv() throws IOException {
    write(
        root,
        "elsewhere/lib/python3.11/site-packages/pkg-1.0.dist-info/METADATA",
        "Name: pkg\nVersion: 1.0\nLicense-Expression: ISC\n");

    assertThat(lookup(root.resolve("elsewhere").toString(), "pkg", VERSION).license().describe())
        .isEqualTo("ISC");
  }

  @Test
  void readsLegacyEggInfoMetadata() throws IOException {
    write(
        root,
        SITE + "old-1.0.egg-info/PKG-INFO",
        "Metadata-Version: 1.1\nName: old\nVersion: 1.0\nLicense: BSD-3-Clause\n");

    assertThat(lookup(null, "old", VERSION).license().describe()).isEqualTo("BSD-3-Clause");
  }
}
