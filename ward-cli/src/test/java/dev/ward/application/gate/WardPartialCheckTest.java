package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateScope;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code ward} check inside a partial run: narrowed to the edited file, or skipped. */
class WardPartialCheckTest {
  private static final String CONFIGURATION =
      """
      version = 2
      [checks.ward]
      uses = "ward"
      profiles = ["default", "edit"]
      rules = ["ward:java/type-name"]
      [checks.ward.paths]
      match = ["src/**/*.java"]
      full = ["config/ward.rules"]
      """;
  private static final String ORDER = "src/main/java/com/acme/order.java";
  private static final String CUSTOMER = "src/main/java/com/acme/customer.java";

  @TempDir Path dir;

  @BeforeEach
  void project() throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("ward.toml"), CONFIGURATION);
    write(ORDER, "package com.acme;\n\nclass order {}\n");
    write(CUSTOMER, "package com.acme;\n\nclass customer {}\n");
  }

  @Test
  void sourceEditRunsTheWardCheckNarrowedAndReportsOnlyTheEditedFile() {
    final CheckReport report = partial(ORDER);

    assertThat(report.results())
        .filteredOn(result -> "ward".equals(result.name()))
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.scope()).isEqualTo(GateScope.NARROWED);
              assertThat(result.status()).isEqualTo(GateStatus.FAILED);
              assertThat(result.findings().items())
                  .extracting(Finding::file)
                  .containsExactly(ORDER);
            });
  }

  @Test
  void editOutsideTheSourcesSkipsTheWardCheck() {
    final CheckReport report = partial("README.md");

    assertThat(report.results())
        .filteredOn(result -> "ward".equals(result.name()))
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.scope()).isEqualTo(GateScope.SKIPPED);
              assertThat(result.status()).isEqualTo(GateStatus.SKIPPED);
            });
  }

  @Test
  void ruleConfigurationEditRunsTheWardCheckInFullOverTheWholeProject() {
    final CheckReport report = partial("config/ward.rules");

    assertThat(report.results())
        .filteredOn(result -> "ward".equals(result.name()))
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.scope()).isEqualTo(GateScope.FULL);
              assertThat(result.findings().items())
                  .extracting(Finding::file)
                  .containsExactlyInAnyOrder(ORDER, CUSTOMER);
            });
  }

  @Test
  void theCheckFindingsOfAnUnchangedFileAreNotReported() {
    final GateResult result = partial(ORDER).results().getFirst();

    assertThat(result.findings().items())
        .extracting(Finding::file)
        .containsOnly(ORDER)
        .doesNotContain(CUSTOMER);
  }

  private void write(final String relative, final String content) throws IOException {
    Files.createDirectories(dir.resolve(relative).getParent());
    Files.writeString(dir.resolve(relative), content);
  }

  private CheckReport partial(final String path) {
    return WardAnalysisFixtures.checks(new FakeCommandExecutor(0, "", ""))
        .checkPartial(dir, List.of(dir.resolve(path)), Set.of());
  }
}
