package dev.ward.bench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A gate command as recorded in the result. The first element is {@code ward} for the native
 * executable, so the recorded vector stays the command users type, while the run uses the measured
 * binary.
 */
record GateJob(String name, List<String> recordedCommand, boolean editHook) {

  static final String WARD = "ward";

  List<String> executable(final Path binary) {
    final List<String> vector = new ArrayList<>(recordedCommand);
    if (WARD.equals(vector.get(0))) {
      vector.set(0, binary.toString());
    }
    return List.copyOf(vector);
  }
}
