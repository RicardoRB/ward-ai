package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.Simulators.Simulator;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Picks an iPhone of the newest iOS runtime and boots it through {@code xcrun simctl}. */
class SimctlSimulatorsTest {
  static final Path ROOT = Path.of("/repo/shop");
  private static final String IOS_26 = "com.apple.CoreSimulator.SimRuntime.iOS-26-5";
  private static final String IOS_27 = "com.apple.CoreSimulator.SimRuntime.iOS-27-0";
  static final String RUNTIMES =
      """
      {"runtimes": [
        {"identifier": "%s", "name": "iOS 26.5", "platform": "iOS", "version": "26.5",
         "isAvailable": true, "buildversion": "23F73"},
        {"identifier": "com.apple.CoreSimulator.SimRuntime.watchOS-27-0", "name": "watchOS 27.0",
         "platform": "watchOS", "version": "27.0", "isAvailable": true},
        {"identifier": "%s", "name": "iOS 27.0", "platform": "iOS", "version": "27.0",
         "isAvailable": true, "buildversion": "24A434"}
      ]}
      """
          .formatted(IOS_26, IOS_27);
  private static final String NEW_PRO = "84D0A34E-0000-0000-0000-000000000001";
  static final String NEW_BASE = "84D0A34E-0000-0000-0000-000000000002";

  static String devices(final String newestState) {
    return """
        {"devices": {
          "%s": [
            {"udid": "723003EC-0000-0000-0000-000000000001", "name": "iPhone 17 Pro",
             "state": "Shutdown", "isAvailable": true,
             "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"}
          ],
          "%s": [
            {"udid": "%s", "name": "iPhone 18 Pro", "state": "Shutdown", "isAvailable": true,
             "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPhone-18-Pro"},
            {"udid": "IPAD", "name": "iPad Air", "state": "Shutdown", "isAvailable": true,
             "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPad-Air-11"},
            {"udid": "%s", "name": "iPhone 18", "state": "%s", "isAvailable": true,
             "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPhone-18"}
          ]
        }}
        """
        .formatted(IOS_26, IOS_27, NEW_PRO, NEW_BASE, newestState);
  }

  static CommandResult ok(final String stdout) {
    return new CommandResult(0, stdout, "", Duration.ZERO);
  }

  static Simulator ready(final FakeCommandExecutor commands) {
    return new SimctlSimulators(commands, new ForyJsonCodec()).readyIPhone(ROOT);
  }

  @Test
  void bootsTheFirstIPhoneByNameOfTheNewestRuntimeAndWaitsForIt() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(ok(RUNTIMES), ok(devices("Shutdown")), ok(""), ok(""));

    assertThat(ready(commands)).isEqualTo(Simulator.ready(NEW_BASE));
    assertThat(commands.commands)
        .containsExactly(
            List.of("xcrun", "simctl", "list", "runtimes", "-j"),
            List.of("xcrun", "simctl", "list", "devices", "available", "-j"),
            List.of("xcrun", "simctl", "boot", NEW_BASE),
            List.of("xcrun", "simctl", "bootstatus", NEW_BASE, "-b"));
    assertThat(commands.workingDirectories).containsOnly(ROOT);
  }

  @Test
  void alreadyBootedDeviceIsOnlyAwaited() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(ok(RUNTIMES), ok(devices("Booted")), ok(""));

    assertThat(ready(commands)).isEqualTo(Simulator.ready(NEW_BASE));
    assertThat(commands.commands)
        .last()
        .isEqualTo(List.of("xcrun", "simctl", "bootstatus", NEW_BASE, "-b"));
    assertThat(commands.commands).noneMatch(command -> command.contains("boot"));
  }

  @Test
  void noIosRuntimeSaysHowToInstallOne() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(ok("{\"runtimes\": []}"), ok("{\"devices\": {}}"));

    final Simulator simulator = ready(commands);

    assertThat(simulator.isReady()).isFalse();
    assertThat(simulator.problem()).isEqualTo(SimctlSimulators.NO_RUNTIME);
    assertThat(commands.commands).hasSize(2);
  }

  @Test
  void runtimeWithoutAnIPhoneIsUnavailable() {
    final String ipadOnly =
        """
        {"devices": {"%s": [
          {"udid": "IPAD", "name": "iPad Air", "state": "Shutdown", "isAvailable": true,
           "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPad-Air-11"}
        ]}}
        """
            .formatted(IOS_27);
    final FakeCommandExecutor commands = new FakeCommandExecutor(ok(RUNTIMES), ok(ipadOnly));

    final Simulator simulator = ready(commands);

    assertThat(simulator.isReady()).isFalse();
    assertThat(simulator.problem()).contains("iOS 27.0", "no available iPhone");
    assertThat(commands.commands).hasSize(2);
  }

  @Test
  void failingSimctlOrUnreadableOutputIsUnavailable() {
    assertThat(ready(new FakeCommandExecutor(1, "", "CoreSimulator is broken")).problem())
        .contains("xcrun simctl list");
    assertThat(ready(new FakeCommandExecutor(0, "not json", "")).isReady()).isFalse();
  }
}
