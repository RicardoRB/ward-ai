package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateFindings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The removed {@code sonar} type is unknown, and a command that runs the tool is a plain check. */
class RemovedSonarGateCheckTest {
  @Test
  void theGateFactoryRejectsTheSonarTypeAsUnknown() {
    final WardConfiguration configuration =
        new WardConfiguration(
            1, Map.of("sonar", new GateConfiguration("sonar", true, List.of("sonar"))));

    assertThatThrownBy(
            () -> new QualityGateFactory(new FakeCommandExecutor(0, "", "")).create(configuration))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'sonar'");
  }

  @Test
  void aCommandThatRunsSonarIsAPlainCommandCheckWithoutFindings(@TempDir final Path dir)
      throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"), "version = 2\n[checks.scan]\ncommand = \"mvn sonar:sonar\"\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");

    final CheckReport report = CheckServiceFixtures.checks(executor).check(dir);

    assertThat(executor.commands).containsExactly(List.of("mvn", "sonar:sonar"));
    assertThat(report.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.type()).isEqualTo("command");
              assertThat(result.findings()).isEqualTo(GateFindings.NONE);
            });
  }
}
