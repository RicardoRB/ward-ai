package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.license.LicenseFixtures;
import dev.ward.application.license.LicenseService;
import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicenseGateTest {

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
  void passesWhenEveryLicenseIsPermissive() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "left-pad", "1.3.0", DependencyScope.RUNTIME, PACKAGE_JSON)),
            Map.of("left-pad", LicenseFixtures.declared("MIT")));

    final GateResult result = run(service, LicenseSettings.defaults());

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.name()).isEqualTo("license");
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void deniedLicenseFailsWithTheFileAndThePackage() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.MAVEN,
                    "com.example:gpl-lib",
                    "1.0",
                    DependencyScope.RUNTIME,
                    "pom.xml")),
            Map.of("com.example:gpl-lib", LicenseFixtures.declared("GPL-3.0-only")));

    final GateResult result = run(service, LicenseSettings.defaults());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo(LicenseGate.DENIED);
    assertThat(finding.file()).isEqualTo("pom.xml");
    assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(finding.message()).contains("com.example:gpl-lib:1.0").contains("GPL-3.0-only");
    assertThat(finding.gateId()).isEqualTo("license");
  }

  @Test
  void allowListFailsWithNotAllowed() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "lib", "1.0.0", DependencyScope.RUNTIME, PACKAGE_JSON)),
            Map.of("lib", LicenseFixtures.declared("EPL-2.0")));
    final LicenseSettings allow =
        new LicenseSettings(
            LicenseSettings.Mode.ALLOW, List.of("Apache-2.0", "MIT"), List.of(), Set.of(), true);

    final GateResult result = run(service, allow);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().ruleId()).isEqualTo(LicenseGate.NOT_ALLOWED);
  }

  @Test
  void scopeFilterIgnoresPackagesOutsideTheScopes() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "gpl-test", "1.0.0", DependencyScope.TEST, PACKAGE_JSON)),
            Map.of("gpl-test", LicenseFixtures.declared("GPL-3.0-only")));

    assertThat(run(service, deny(true, Set.of("runtime"))).status()).isEqualTo(GateStatus.PASSED);
    assertThat(run(service, deny(true, Set.of())).status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void dualLicensedPackagePasses() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "dual", "1.0.0", DependencyScope.RUNTIME, PACKAGE_JSON)),
            Map.of("dual", LicenseFixtures.declared("MIT OR GPL-3.0-only")));

    assertThat(run(service, LicenseSettings.defaults()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void findingsAreOrderedAndStableAcrossRuns() {
    final LicenseService service =
        LicenseFixtures.service(
            LicenseFixtures.inventory(
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "b-gpl", "1.0.0", DependencyScope.RUNTIME, PACKAGE_JSON),
                LicenseFixtures.dependency(
                    Ecosystem.NPM, "a-gpl", "1.0.0", DependencyScope.RUNTIME, PACKAGE_JSON)),
            Map.of(
                "a-gpl",
                LicenseFixtures.declared("GPL-3.0-only"),
                "b-gpl",
                LicenseFixtures.declared("AGPL-3.0-only")));

    final List<Finding> first = run(service, LicenseSettings.defaults()).findings().items();
    final List<Finding> second = run(service, LicenseSettings.defaults()).findings().items();

    assertThat(first).isEqualTo(second);
    assertThat(first.get(0).message()).contains("a-gpl");
    assertThat(first.get(1).message()).contains("b-gpl");
  }
}
