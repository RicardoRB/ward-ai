package dev.ward.infrastructure.gate;

import dev.ward.domain.gate.Simulators;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/** Finds and boots an iPhone simulator with {@code xcrun simctl}. */
public final class SimctlSimulators implements Simulators {
  static final String NO_RUNTIME =
      "No iOS simulator runtime is installed. Install one in Xcode > Settings > Components, or"
          + " run `xcodebuild -downloadPlatform iOS`.";
  private static final Logger LOGGER = Logger.getLogger(SimctlSimulators.class.getName());
  private static final String XCRUN = "xcrun";
  private static final String SIMCTL = "simctl";
  private static final String IOS = "iOS";
  private static final String IPHONE_TYPE = ".iPhone";
  private static final String BOOTED = "Booted";
  private static final String ALREADY_BOOTED = "current state: Booted";
  private static final String LIST_FAILED =
      "Ward could not list the simulators. Run `xcrun simctl list` to check CoreSimulator.";

  private final CommandExecutor commands;
  private final JsonCodec codec;

  /** Creates the adapter; {@code commands} runs {@code xcrun simctl}, {@code codec} reads it. */
  @Inject
  public SimctlSimulators(final CommandExecutor commands, final JsonCodec codec) {
    this.commands = commands;
    this.codec = codec;
  }

  @Override
  public Simulator readyIPhone(final Path workingDirectory) {
    final Optional<RuntimeList> runtimes = list(workingDirectory, "runtimes", RuntimeList.class);
    final Optional<DeviceList> devices = list(workingDirectory, "devices", DeviceList.class);
    if (runtimes.isEmpty() || devices.isEmpty()) {
      return unavailable("list-failed", LIST_FAILED);
    }
    final List<SimRuntime> newestFirst = iosRuntimesNewestFirst(runtimes.get());
    if (newestFirst.isEmpty()) {
      return unavailable("no-runtime", NO_RUNTIME);
    }
    // simctl lists a runtime as available before its files exist, so a boot can fail; try the next.
    final List<Simulator> failures = new ArrayList<>();
    for (final SimRuntime runtime : newestFirst) {
      final Optional<SimDevice> device = firstIPhone(devices.get(), runtime.identifier);
      if (device.isEmpty()) {
        failures.add(
            unavailable(
                "no-iphone",
                "The "
                    + runtime.name
                    + " simulator runtime has no available iPhone. Create one in Xcode > Window >"
                    + " Devices and Simulators, or with `xcrun simctl create`."));
        continue;
      }
      LOGGER.log(
          Level.INFO,
          () ->
              "event=simulator-selected runtime="
                  + runtime.identifier
                  + " state="
                  + device.get().state);
      final Simulator simulator = boot(device.get(), workingDirectory);
      if (simulator.problem() == null) {
        return simulator;
      }
      failures.add(simulator);
    }
    // The first failure names the newest runtime, which is the one the user is most likely to fix.
    return failures.getFirst();
  }

  private <T> Optional<T> list(final Path directory, final String what, final Class<T> type) {
    final List<String> command =
        "devices".equals(what)
            ? List.of(XCRUN, SIMCTL, "list", what, "available", "-j")
            : List.of(XCRUN, SIMCTL, "list", what, "-j");
    final CommandResult result = commands.execute(command, directory);
    if (result.hasExecutionError() || !result.succeeded()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(codec.fromJson(result.stdout(), type));
    } catch (JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(Level.INFO, () -> "event=simulator-list-unreadable list=" + what);
      return Optional.empty();
    }
  }

  /** Returns the available iOS runtimes, the newest version first. */
  private static List<SimRuntime> iosRuntimesNewestFirst(final RuntimeList runtimes) {
    return runtimes.runtimesOrEmpty().stream()
        .filter(
            runtime -> IOS.equals(runtime.platform) && !Boolean.FALSE.equals(runtime.isAvailable))
        .filter(runtime -> runtime.identifier != null && runtime.version != null)
        .sorted(
            Comparator.comparing((SimRuntime runtime) -> version(runtime.version), Arrays::compare)
                .reversed())
        .toList();
  }

  /** Splits {@code 26.5} into {@code [26, 5]}; parts that are not numbers count as 0. */
  private static int[] version(final String version) {
    return Arrays.stream(version.split("\\.")).mapToInt(SimctlSimulators::number).toArray();
  }

  private static int number(final String part) {
    try {
      return Integer.parseInt(part);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private static Optional<SimDevice> firstIPhone(final DeviceList devices, final String runtime) {
    return devices.devicesOf(runtime).stream()
        .filter(device -> device.udid != null && device.name != null)
        .filter(device -> !Boolean.FALSE.equals(device.isAvailable))
        .filter(device -> Objects.toString(device.deviceTypeIdentifier, "").contains(IPHONE_TYPE))
        .min(Comparator.comparing(device -> device.name));
  }

  private Simulator boot(final SimDevice device, final Path directory) {
    if (!BOOTED.equals(device.state)) {
      final CommandResult boot =
          commands.execute(List.of(XCRUN, SIMCTL, "boot", device.udid), directory);
      if (!boot.succeeded() && !Objects.toString(boot.stderr(), "").contains(ALREADY_BOOTED)) {
        return unavailable(
            "boot-failed",
            "The "
                + device.name
                + " simulator did not boot. Run `xcrun simctl boot "
                + device.udid
                + "` to see why.");
      }
    }
    final CommandResult status =
        commands.execute(List.of(XCRUN, SIMCTL, "bootstatus", device.udid, "-b"), directory);
    if (!status.succeeded()) {
      return unavailable(
          "boot-unfinished",
          "The " + device.name + " simulator did not finish booting. " + LIST_FAILED);
    }
    return Simulator.ready(device.udid);
  }

  private static Simulator unavailable(final String reason, final String problem) {
    LOGGER.log(Level.INFO, () -> "event=simulator-unavailable reason=" + reason);
    return Simulator.unavailable(problem);
  }

  @JsonType
  static final class RuntimeList {
    List<SimRuntime> runtimes;

    List<SimRuntime> runtimesOrEmpty() {
      return runtimes == null ? List.of() : runtimes.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class SimRuntime {
    String identifier;
    String name;
    String platform;
    String version;
    Boolean isAvailable;
  }

  @JsonType
  static final class DeviceList {
    Map<String, List<SimDevice>> devices;

    List<SimDevice> devicesOf(final String runtime) {
      final List<SimDevice> found = devices == null ? null : devices.get(runtime);
      return found == null ? List.of() : found.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class SimDevice {
    String udid;
    String name;
    String state;
    String deviceTypeIdentifier;
    Boolean isAvailable;
  }
}
