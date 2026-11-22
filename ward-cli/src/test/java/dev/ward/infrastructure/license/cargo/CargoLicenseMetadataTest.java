package dev.ward.infrastructure.license.cargo;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.application.license.LicenseService;
import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.cargo.CargoDependencySource;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CargoLicenseMetadataTest {
  private static final String REGISTRY = "registry/src/index.crates.io-1949cf8c6b5b557f/";

  @TempDir Path root;
  @TempDir Path cargoHome;

  private Project rustProject() {
    return project(root, Language.RUST, BuildTool.CARGO);
  }

  private LicenseLookup lookup(final String name, final String version) {
    return new CargoLicenseMetadata(cargoHome)
        .lookup(rustProject(), "", dependency(Ecosystem.CRATES_IO, name, version));
  }

  private void crate(final String name, final String version, final String licenseLine)
      throws IOException {
    write(
        cargoHome,
        REGISTRY + name + "-" + version + "/Cargo.toml",
        "[package]\nname = \"" + name + "\"\nversion = \"" + version + "\"\n" + licenseLine + "\n"
    );
  }

  @Test
  void readsTheSpdxExpressionOfTheRegistryCopy() throws IOException {
    crate("itoa", "1.0.19", "license = \"MIT OR Apache-2.0\"");

    assertThat(lookup("itoa", "1.0.19").license().describe()).isEqualTo("MIT OR Apache-2.0");
  }

  @Test
  void aLegacySlashMeansEitherLicense() throws IOException {
    crate("old", "0.1.0", "license = \"MIT/Apache-2.0\"");

    assertThat(lookup("old", "0.1.0").license().describe()).isEqualTo("MIT OR Apache-2.0");
  }

  @Test
  void aVendoredCopyIsRead() throws IOException {
    write(
        root,
        "vendor/fastrand/Cargo.toml",
        "[package]\nname=\"fastrand\"\nversion=\"2.5.0\"\nlicense=\"MIT\"\n"
    );

    assertThat(lookup("fastrand", "2.5.0").license().describe()).isEqualTo("MIT");
  }

  @Test
  void aCrateWithoutALicenseFieldIsNotGuessed() throws IOException {
    crate("nolicense", "1.0.0", "");

    assertThat(lookup("nolicense", "1.0.0").reason())
        .isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }

  @Test
  void aMissingCopyAndAnotherVersionAreReported() throws IOException {
    crate("itoa", "1.0.18", "license = \"MIT\"");

    assertThat(lookup("itoa", "1.0.19").reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
    assertThat(lookup("absent", "1.0.0").reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
  }

  @DisplayName("a license outside the allowed list")
  @Test
  void aLicenseOutsideTheAllowedList() throws IOException {
    write(
        root,
        "Cargo.toml",
        "[package]\nname='app'\nversion='0.1.0'\n[dependencies]\ncopyleft='1'\n"
    );
    write(
        root,
        "Cargo.lock",
        "version = 4\n[[package]]\nname = \"app\"\nversion = \"0.1.0\"\ndependencies = [\"copyleft\"]\n"
            + "[[package]]\nname = \"copyleft\"\nversion = \"1.0.0\"\nsource = \"registry+x\"\n"
    );
    crate("copyleft", "1.0.0", "license = \"GPL-3.0-only\"");
    final Map<Ecosystem, LicenseMetadata> readers = new EnumMap<>(Ecosystem.class);
    readers.put(Ecosystem.CRATES_IO, new CargoLicenseMetadata(cargoHome));
    final LicenseService service =
        new LicenseService(
            new DependencyInventoryService(Map.of(BuildTool.CARGO, new CargoDependencySource())),
            readers,
            new BundledLicenseCatalog()
        );

    final GateResult result =
        new dev.ward.application.gate.LicenseGate("license", service, LicenseSettings.defaults())
            .execute(rustProject());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().message())
        .contains("copyleft:1.0.0")
        .contains("GPL-3.0-only");
  }
}
