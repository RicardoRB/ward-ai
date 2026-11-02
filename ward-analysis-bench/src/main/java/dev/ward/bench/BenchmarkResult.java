package dev.ward.bench;

import java.util.List;

/** The in-memory result of one benchmark run; {@link ResultJson} renders it. */
record BenchmarkResult(
    List<EntryResult> entries,
    NativeExecutable binary,
    int fullRepeats,
    int editRepeats,
    boolean completed) {

  record EntryResult(CorpusEntry entry, List<JobResult> jobs) {}

  /** Metrics for one gate job; {@code edit} is null for jobs that are not edit-hook jobs. */
  record JobResult(
      String name,
      List<String> command,
      boolean completed,
      List<Integer> exitCodes,
      Statistics cold,
      Statistics warm,
      Statistics peakRssKb,
      Statistics edit) {}
}
