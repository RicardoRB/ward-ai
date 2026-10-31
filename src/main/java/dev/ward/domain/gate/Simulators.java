package dev.ward.domain.gate;

import java.nio.file.Path;

/** Port to the simulators installed with Xcode. */
@FunctionalInterface
public interface Simulators {

  /**
   * Picks an iPhone of the newest installed iOS runtime, boots it unless it is booted, and waits
   * until it has finished booting.
   */
  Simulator readyIPhone(Path workingDirectory);

  /**
   * A booted simulator, or why none is ready.
   *
   * @param deviceId the device's UDID, or {@code null} when none is ready
   * @param problem why no simulator is ready, or {@code null} when one is
   */
  record Simulator(String deviceId, String problem) {

    /** Returns the ready device {@code deviceId}. */
    public static Simulator ready(final String deviceId) {
      return new Simulator(deviceId, null);
    }

    /** Returns that no simulator is ready, for {@code problem}. */
    public static Simulator unavailable(final String problem) {
      return new Simulator(null, problem);
    }

    /** Returns whether a device is ready. */
    public boolean isReady() {
      return problem == null;
    }
  }
}
