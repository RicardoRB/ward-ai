package dev.ward.application.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SbomServiceTest {
  private static final Path DIRECTORY = Path.of("/work/app");

  private final SbomService sbom =
      new SbomService(LicenseFixtures.detector(), LicenseFixtures.mixed(), Map.of(), "1.2.3");

  @Test
  void hasTheCycloneDxEnvelopeWithoutSerialNumberOrTimestamp() {
    final String json = sbom.generate(DIRECTORY, Set.of(), Optional.empty()).content();

    assertThat(json)
        .contains("\"bomFormat\": \"CycloneDX\"", "\"specVersion\": \"1.6\"", "\"version\": 1")
        .contains("\"name\": \"ward\"", "\"version\": \"1.2.3\"")
        .doesNotContain("serialNumber", "timestamp")
        .endsWith("}\n");
  }

  @Test
  void componentsCarryPurlsGroupsAndLicenses() {
    final String json = sbom.generate(DIRECTORY, Set.of(), Optional.empty()).content();

    assertThat(json)
        .contains("\"purl\": \"pkg:npm/%40scope/pkg@1.0.0\"")
        .contains("\"group\": \"@scope\"", "\"name\": \"pkg\"")
        .contains("\"group\": \"com.example\"", "\"name\": \"lib\"")
        .contains("\"id\": \"Apache-2.0\"")
        .contains("\"expression\": \"MIT OR GPL-3.0-only\"")
        .contains("\"expression\": \"GPL-2.0-only WITH Classpath-exception-2.0\"");
  }

  @Test
  void rootHasEdgesToDirectPackagesOnlyAndComponentsSortByPurl() {
    final String json = sbom.generate(DIRECTORY, Set.of(), Optional.empty()).content();

    final int root = json.indexOf("\"ref\": \"root\"");
    assertThat(root).isPositive();
    final String edges = json.substring(root, json.indexOf(']', json.indexOf("dependsOn", root)));
    assertThat(edges).contains("pkg:npm/%40scope/pkg@1.0.0", "pkg:npm/mystery@2.0.0");
    assertThat(edges).doesNotContain("left-pad", "com.example");
    assertThat(json.indexOf("pkg:maven/com.example/lib@1.0"))
        .isLessThan(json.indexOf("pkg:npm/%40scope/pkg@1.0.0"));
  }

  @Test
  void leavesUnknownLicensesOutAndWarns() {
    final Document document = sbom.generate(DIRECTORY, Set.of(), Optional.empty());

    assertThat(document.warnings()).singleElement().asString().contains("pkg:npm/mystery@2.0.0");
    final String mystery = document.content().substring(document.content().indexOf("mystery"));
    assertThat(mystery.substring(0, mystery.indexOf('}'))).doesNotContain("licenses");
  }

  @Test
  void honorsTheScopeFilter() {
    final String json = sbom.generate(DIRECTORY, Set.of("runtime"), Optional.empty()).content();

    assertThat(json).doesNotContain("left-pad").contains("mystery");
  }

  @Test
  void aProjectWithoutDependenciesHasNoComponents() {
    final SbomService empty =
        new SbomService(
            LicenseFixtures.detector(),
            LicenseFixtures.service(LicenseFixtures.inventory(), Map.of()),
            Map.of(),
            "1.2.3");

    assertThat(empty.generate(DIRECTORY, Set.of(), Optional.empty()).content())
        .contains("\"components\": []");
  }

  @Test
  void isByteIdenticalOnRepeatedRuns() {
    assertThat(sbom.generate(DIRECTORY, Set.of(), Optional.empty()).content())
        .isEqualTo(sbom.generate(DIRECTORY, Set.of(), Optional.empty()).content());
  }

  @Test
  void anUnusableInventoryProducesNoDocument() {
    final SbomService failing =
        new SbomService(LicenseFixtures.detector(), LicenseFixtures.failing(), Map.of(), "1");

    assertThatThrownBy(() -> failing.generate(DIRECTORY, Set.of(), Optional.empty()))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("npm install");
  }
}
