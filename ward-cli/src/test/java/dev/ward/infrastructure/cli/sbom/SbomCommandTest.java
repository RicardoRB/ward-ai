package dev.ward.infrastructure.cli.sbom;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.license.LicenseFixtures;
import dev.ward.application.license.LicenseService;
import dev.ward.application.license.SbomService;
import dev.ward.infrastructure.cli.CommandFixtures;
import dev.ward.infrastructure.cli.ExitCodes;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SbomCommandTest {
  private final CommandFixtures fixtures = new CommandFixtures();

  private SbomCommand command(final LicenseService licenses) {
    return new SbomCommand(
        new SbomService(LicenseFixtures.detector(), licenses, "9.9"), fixtures.output);
  }

  @Test
  void writesOnlyTheJsonToStandardOutAndWarningsToStandardError() {
    final int code = fixtures.run(command(CommandFixtures.licenses()));

    assertThat(code).isEqualTo(ExitCodes.OK);
    assertThat(fixtures.out.toString()).startsWith("{\n").contains("\"bomFormat\": \"CycloneDX\"");
    assertThat(fixtures.err.toString()).contains("mystery").doesNotContain("bomFormat");
    assertThat(fixtures.written).isEmpty();
  }

  @Test
  void writesToTheOutputFileAndNothingToStandardOut() {
    final int code =
        fixtures.run(
            command(CommandFixtures.licenses()),
            "--directory",
            "/work/app",
            "--output",
            "sbom.json");

    assertThat(code).isEqualTo(ExitCodes.OK);
    assertThat(fixtures.out.toString()).isEmpty();
    assertThat(fixtures.written).containsOnlyKeys(Path.of("sbom.json"));
  }

  @Test
  void scopesNarrowTheDocument() {
    fixtures.run(command(CommandFixtures.licenses()), "--scopes", "runtime");

    assertThat(fixtures.out.toString()).contains("left-pad").doesNotContain("mystery");
    assertThat(fixtures.err.toString()).isEmpty();
  }

  @Test
  void anUnknownScopeIsAnError() {
    final int code = fixtures.run(command(CommandFixtures.licenses()), "--scopes", "compile");

    assertThat(code).isEqualTo(ExitCodes.ERROR);
    assertThat(fixtures.err.toString()).contains("--scopes");
    assertThat(fixtures.out.toString()).isEmpty();
  }

  @Test
  void anUnusableInventoryWritesNothingAndExitsWithTwo() {
    final int code = fixtures.run(command(LicenseFixtures.failing()), "--output", "sbom.json");

    assertThat(code).isEqualTo(ExitCodes.ERROR);
    assertThat(fixtures.err.toString()).contains("ward sbom:").contains("npm install");
    assertThat(fixtures.out.toString()).isEmpty();
    assertThat(fixtures.written).isEmpty();
  }
}
