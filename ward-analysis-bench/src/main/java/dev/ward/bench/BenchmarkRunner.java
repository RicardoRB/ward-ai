package dev.ward.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Measures every gate job on every corpus entry. */
final class BenchmarkRunner {

  static final int EDIT_REPEATS = 20;

  /** The preset every corpus entry is initialised with, so each entry runs the same gates. */
  static final String PRESET = "standard";

  /** The manifest entry whose Checkstyle edit-hook job is measured. */
  static final String WARD_ENTRY = "ward";

  private static final Logger LOG = Logger.getLogger(BenchmarkRunner.class.getName());

  private final CommandRunner runner;
  private final CorpusFetcher fetcher;
  private final Path binary;
  private final Path cacheRoot;
  private final Path workRoot;
  private final int fullRepeats;

  BenchmarkRunner(
      final CommandRunner runner,
      final Path binary,
      final Path cacheRoot,
      final Path workRoot,
      final int fullRepeats) {
    this.runner = runner;
    this.fetcher = new CorpusFetcher(runner);
    this.binary = binary;
    this.cacheRoot = cacheRoot;
    this.workRoot = workRoot;
    this.fullRepeats = fullRepeats;
  }

  BenchmarkResult run(final List<CorpusEntry> entries, final NativeExecutable executable) {
    final List<BenchmarkResult.EntryResult> results =
        entries.stream().map(this::measureEntry).toList();
    final boolean completed =
        results.stream()
            .flatMap(entry -> entry.jobs().stream())
            .allMatch(BenchmarkResult.JobResult::completed);
    return new BenchmarkResult(results, executable, fullRepeats, EDIT_REPEATS, completed);
  }

  static List<GateJob> jobsFor(final CorpusEntry entry) {
    final List<GateJob> jobs =
        new ArrayList<>(
            List.of(
                new GateJob(
                    "ward-check-ci", List.of(GateJob.WARD, "check", "--profile", "ci"), false),
                new GateJob(
                    "ward-check-default",
                    List.of(GateJob.WARD, "check", "--profile", "default"),
                    true)));
    if (WARD_ENTRY.equals(entry.name())) {
      jobs.add(new GateJob("checkstyle-edit-hook", List.of("./mvnw", "checkstyle:check"), true));
    }
    return List.copyOf(jobs);
  }

  private BenchmarkResult.EntryResult measureEntry(final CorpusEntry entry) {
    LOG.info(() -> "Fetching corpus entry " + entry.name());
    final Path checkout = fetcher.fetch(entry, cacheRoot);
    initialise(entry, checkout);
    final List<BenchmarkResult.JobResult> jobs =
        jobsFor(entry).stream().map(job -> measureJob(entry, checkout, job)).toList();
    return new BenchmarkResult.EntryResult(entry, jobs);
  }

  private void initialise(final CorpusEntry entry, final Path checkout) {
    if (Files.exists(checkout.resolve("ward.toml"))) {
      return;
    }
    final CommandResult init =
        runner.run(List.of(binary.toString(), "init", "--preset", PRESET, "--yes"), checkout);
    if (init.exitCode() != 0) {
      throw new IllegalStateException("ward init failed for corpus repository " + entry.name());
    }
  }

  private BenchmarkResult.JobResult measureJob(
      final CorpusEntry entry, final Path checkout, final GateJob job) {
    LOG.info(() -> "Measuring " + job.name() + " on " + entry.name());
    final Path jobRoot = workRoot.resolve(entry.name()).resolve(job.name());
    final List<CommandResult> cold = new ArrayList<>();
    final List<CommandResult> warm = new ArrayList<>();
    for (int repeat = 1; repeat <= fullRepeats; repeat++) {
      final Path copy = jobRoot.resolve("run-" + repeat);
      FileTrees.delete(copy);
      FileTrees.copy(checkout, copy);
      cold.add(runner.run(job.executable(binary), copy));
      warm.add(runner.run(job.executable(binary), copy));
      FileTrees.delete(copy);
    }
    final Edits edits =
        job.editHook() ? measureEdits(entry, checkout, job, jobRoot.resolve("edit")) : null;
    final boolean completed =
        cold.stream().allMatch(result -> !result.timedOut())
            && warm.stream().allMatch(result -> !result.timedOut())
            && (edits == null || edits.completed());
    return new BenchmarkResult.JobResult(
        job.name(),
        job.recordedCommand(),
        completed,
        cold.stream().map(CommandResult::exitCode).toList(),
        Statistics.of(cold.stream().map(CommandResult::elapsedMillis).toList()),
        Statistics.of(warm.stream().map(CommandResult::elapsedMillis).toList()),
        Statistics.of(peakRss(cold, warm)),
        edits == null ? null : edits.latency());
  }

  private static List<Double> peakRss(
      final List<CommandResult> cold, final List<CommandResult> warm) {
    return java.util.stream.Stream.concat(cold.stream(), warm.stream())
        .map(result -> (double) result.peakTreeRssKb())
        .toList();
  }

  /** Edit-latency samples for one edit-hook job, and whether every edit run finished. */
  private record Edits(Statistics latency, boolean completed) {}

  /** Primes a warm copy, then times one appended-newline edit per run. */
  private Edits measureEdits(
      final CorpusEntry entry, final Path checkout, final GateJob job, final Path copy) {
    FileTrees.delete(copy);
    FileTrees.copy(checkout, copy);
    runner.run(job.executable(binary), copy);
    final List<CommandResult> edits = new ArrayList<>();
    for (int edit = 1; edit <= EDIT_REPEATS; edit++) {
      SourceEdits.appendNewline(SourceEdits.target(copy, entry.language()));
      edits.add(runner.run(job.executable(binary), copy));
    }
    FileTrees.delete(copy);
    final boolean completed = edits.stream().allMatch(result -> !result.timedOut());
    return new Edits(
        Statistics.of(edits.stream().map(CommandResult::elapsedMillis).toList()), completed);
  }
}
