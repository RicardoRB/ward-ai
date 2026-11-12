package dev.ward.infrastructure.cli.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.license.LicenseFixtures;
import dev.ward.application.license.LicenseService;
import dev.ward.application.license.NoticeService;
import dev.ward.infrastructure.cli.CommandFixtures;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.license.BundledLicenseTexts;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LicensesCommandTest {
  private final CommandFixtures fixtures = new CommandFixtures();

  private LicensesCommand command(final LicenseService licenses) {
    return new LicensesCommand(
        new NoticeService(LicenseFixtures.detector(), licenses, new BundledLicenseTexts()),
        fixtures.output);
  }

  @Test
  void printsTheNotice() {
    final int code = fixtures.run(command(CommandFixtures.licenses()), "--scopes", "runtime");

    assertThat(code).isEqualTo(ExitCodes.OK);
    assertThat(fixtures.out.toString())
        .startsWith("Lists of 1 third-party dependencies.")
        .contains("MIT");
  }

  @Test
  void writesTheOutputFile() {
    final int code = fixtures.run(command(CommandFixtures.licenses()), "-o", "/tmp/notice.txt");

    assertThat(code).isEqualTo(ExitCodes.OK);
    assertThat(fixtures.written).containsOnlyKeys(Path.of("/tmp/notice.txt"));
    assertThat(fixtures.err.toString()).contains("mystery");
  }

  @Test
  void reportsAnUnusableInventoryWithExitCodeTwo() {
    final int code = fixtures.run(command(LicenseFixtures.failing()));

    assertThat(code).isEqualTo(ExitCodes.ERROR);
    assertThat(fixtures.err.toString()).contains("ward licenses:");
    assertThat(fixtures.written).isEmpty();
  }
}
