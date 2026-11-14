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

/** The removed {@code zap} type is unknown, and a command that runs the tool is a plain check. */
class RemovedZapGateCheckTest {
  @Test
  void theGateFactoryRejectsTheZapTypeAsUnknown() {
    final WardConfiguration configuration =
        new WardConfiguration(
            1, Map.of("zap", new GateConfiguration("zap", true, List.of("zap-baseline.py"))));

    assertThatThrownBy(
            () -> new QualityGateFactory(new FakeCommandExecutor(0, "", "")).create(configuration))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'zap'");
  }

  @Test
  void aCommandThatRunsZapIsAPlainCommandCheckWithoutFindings(@TempDir final Path dir)
      throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"),
        "version = 2\n[checks.scan]\ncommand = \"zap-baseline.py -t http://localhost:8080\"\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");

    final CheckReport report = CheckServiceFixtures.checks(executor).check(dir);

    assertThat(executor.commands)
        .containsExactly(List.of("zap-baseline.py", "-t", "http://localhost:8080"));
    assertThat(report.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.type()).isEqualTo("command");
              assertThat(result.findings()).isEqualTo(GateFindings.NONE);
            });
  }
}
