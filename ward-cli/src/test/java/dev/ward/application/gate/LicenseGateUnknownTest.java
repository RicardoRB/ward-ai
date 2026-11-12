package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.license.LicenseFixtures;
import dev.ward.application.license.LicenseService;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.license.LicenseLookup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicenseGateUnknownTest {

  private static final String PACKAGE_JSON = "package.json";

  private static LicenseSettings deny(final boolean unknownFails, final Set<String> scopes) {
    return new LicenseSettings(
        LicenseSettings.Mode.DENY,
        LicenseSettings.DEFAULT_DENY,
        LicenseSettings.DEFAULT_EXCEPTIONS,
        scopes,
        unknownFails);
  }

  private static GateResult run(final LicenseService service, final LicenseSettings settings) {
    return new LicenseGate("license", service, settings).execute(LicenseFixtures.NPM);
  }

  @Test
  void packageThatIsNotInstalledIsUnknownAndFails() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "lodash", "4.17.21", DependencyScope.RUNTIME, "")),
            Map.of());

    final GateResult result = run(service, LicenseSettings.defaults());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo(LicenseGate.UNKNOWN);
    assertThat(finding.file()).isEqualTo("package-lock.json");
    assertThat(finding.message()).contains("not installed");
  }

  @Test
  void unrecognizedDeclaredTextIsUnknownAndCarriesTheText() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "odd", "1.0.0", DependencyScope.RUNTIME, PACKAGE_JSON)),
            Map.of("odd", LicenseFixtures.declared("See LICENSE file")));

    final GateResult result = run(service, LicenseSettings.defaults());

    assertThat(result.findings().items().getFirst().message()).contains("See LICENSE file");
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void unknownAsWarningDoesNotFailTheGate() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "odd", "1.0.0", DependencyScope.RUNTIME, PACKAGE_JSON)),
            Map.of("odd", LicenseLookup.missing(LicenseLookup.Reason.NO_LICENSE_DECLARED, "none")));

    final GateResult result = run(service, deny(false, Set.of()));

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.findings().items().getFirst().severity()).isEqualTo(FindingSeverity.WARNING);
    assertThat(result.warnings()).isEqualTo(1);
    assertThat(result.errors()).isZero();
  }

  @Test
  void projectWithoutDependenciesPasses() {
    final GateResult result =
        run(
            LicenseFixtures.service(LicenseFixtures.inventory(), Map.of()),
            LicenseSettings.defaults());

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void unusableInventoryIsAnExecutionError() {
    final LicenseService service =
        LicenseFixtures.service(
            project -> {
              throw new DependencyInventoryException(
                  "/work/app", "package-lock.json", "is stale", "npm install");
            },
            Map.of());

    final GateResult result = run(service, LicenseSettings.defaults());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("npm install");
  }

  @Test
  void factoryBuildsTheLicenseGateWithDefaultsWhenNoSettingsAreGiven() {
    final GateConfiguration check = new GateConfiguration("license", true, List.of());
    final GateExtensions extensions =
        new GateExtensions(
            null, null, null, LicenseFixtures.service(LicenseFixtures.inventory(), Map.of()));

    assertThat(extensions.licenseGate(check).execute(LicenseFixtures.NPM).status())
        .isEqualTo(GateStatus.PASSED);
    assertThatThrownBy(() -> GateExtensions.NONE.licenseGate(check))
        .hasMessageContaining("unavailable");
  }
}
