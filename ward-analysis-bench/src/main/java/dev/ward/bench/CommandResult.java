package dev.ward.bench;

/** The measured outcome of one command run. Holds no process output. */
record CommandResult(int exitCode, long elapsedNanos, long peakTreeRssKb, boolean timedOut) {

  private static final double NANOS_PER_MILLI = 1_000_000.0;

  double elapsedMillis() {
    return elapsedNanos / NANOS_PER_MILLI;
  }
}
