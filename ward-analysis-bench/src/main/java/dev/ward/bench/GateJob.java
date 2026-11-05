package dev.ward.bench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A gate command as recorded in the result. The first element is {@code ward} for the native
 * executable, so the recorded vector stays the command users type, while the run uses the measured
 * binary. A {@code wardProbe} job runs the {@code ward} check alone on the file each edit changes:
 * its recorded vector names that file as {@link #EDITED}, and the run substitutes its path.
 */
record GateJob(String name, List<String> recordedCommand, boolean editHook, boolean wardProbe) {

  static final String WARD = "ward";
  static final String EDITED = "{edited}";

  /** A job that is not a ward probe, as the bench measured before the probe existed. */
  GateJob(final String name, final List<String> recordedCommand, final boolean editHook) {
    this(name, recordedCommand, editHook, false);
  }

  List<String> executable(final Path binary) {
    final List<String> vector = new ArrayList<>(recordedCommand);
    if (WARD.equals(vector.get(0))) {
      vector.set(0, binary.toString());
    }
    return List.copyOf(vector);
  }

  /** The executable vector with {@link #EDITED} replaced by the project-relative edited path. */
  List<String> executable(final Path binary, final String editedPath) {
    final List<String> vector = new ArrayList<>(executable(binary));
    vector.replaceAll(argument -> EDITED.equals(argument) ? editedPath : argument);
    return List.copyOf(vector);
  }
}
