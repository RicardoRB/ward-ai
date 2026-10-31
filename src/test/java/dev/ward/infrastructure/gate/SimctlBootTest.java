package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.Simulators.Simulator;
import dev.ward.domain.process.CommandResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Boot failures of the simulator: the next runtime's iPhone is tried, and the first failure is
 * kept.
 */
class SimctlBootTest {
  @Test
  void failedBootIsUnavailable() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.RUNTIMES),
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.devices("Shutdown")),
            new CommandResult(1, "", "Invalid device", Duration.ZERO),
            new CommandResult(1, "", "Invalid device", Duration.ZERO));

    assertThat(SimctlSimulatorsTest.ready(commands).problem())
        .contains("did not boot", SimctlSimulatorsTest.NEW_BASE);
  }

  @Test
  void aFailedBootFallsBackToTheNextRuntimesIPhone() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.RUNTIMES),
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.devices("Shutdown")),
            new CommandResult(1, "", "runtime path not found", Duration.ZERO),
            SimctlSimulatorsTest.ok(""),
            SimctlSimulatorsTest.ok(""));

    final Simulator simulator = SimctlSimulatorsTest.ready(commands);

    assertThat(simulator.isReady()).isTrue();
    assertThat(simulator.deviceId()).isEqualTo("723003EC-0000-0000-0000-000000000001");
  }

  @Test
  void bootedMeanwhileIsReadyButUnfinishedBootIsNot() {
    final FakeCommandExecutor meanwhile =
        new FakeCommandExecutor(
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.RUNTIMES),
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.devices("Shutdown")),
            new CommandResult(
                149, "", "Unable to boot device in current state: Booted", Duration.ZERO),
            SimctlSimulatorsTest.ok(""));

    assertThat(SimctlSimulatorsTest.ready(meanwhile).isReady()).isTrue();
    final FakeCommandExecutor unfinished =
        new FakeCommandExecutor(
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.RUNTIMES),
            SimctlSimulatorsTest.ok(SimctlSimulatorsTest.devices("Booted")),
            new CommandResult(-1, "", "", Duration.ZERO, "timed out"));

    assertThat(SimctlSimulatorsTest.ready(unfinished).problem()).contains("did not finish booting");
  }
}
