package dev.ward.application.gate;

import dev.ward.analysis.domain.port.WorkLimit;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.FindingExtractionException;
import dev.ward.domain.gate.FindingExtractor;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Executes gates and aggregates their results in order. A failing gate does not stop the others.
 */
public class QualityGateRunner {
  private static final Logger LOGGER = Logger.getLogger(QualityGateRunner.class.getName());
  private static final int SERIAL = 1;
  private static final int NONE = -1;

  private final FindingExtractor findingExtractor;
  private final GatePrerequisites prerequisites;

  /** Creates a runner that extracts no findings and explains only missing executables. */
  public QualityGateRunner() {
    this(FindingExtractor.NONE);
  }

  /** Creates a runner; {@code findingExtractor} turns each result into structured findings. */
  public QualityGateRunner(final FindingExtractor findingExtractor) {
    this(findingExtractor, new GatePrerequisites());
  }

  /**
   * Creates a runner; {@code prerequisites} explains gates that could not run in this environment.
   */
  @Inject
  public QualityGateRunner(
      final FindingExtractor findingExtractor,
      final GatePrerequisites prerequisites
  ) {
    this.findingExtractor = findingExtractor;
    this.prerequisites = prerequisites;
  }

  /** Runs every gate in order, without progress output. */
  public CheckReport run(final Project project, final List<QualityGate> gates) {
    return run(project, gates, CheckProgressListener.NO_OP, false);
  }

  /** Runs every gate in order, reporting progress to {@code listener}. */
  public CheckReport run(
      final Project project,
      final List<QualityGate> gates,
      final CheckProgressListener listener
  ) {
    return run(project, gates, listener, false);
  }

  /** Runs the gates in order, skipping the rest after the first failure when {@code failFast}. */
  public CheckReport run(
      final Project project,
      final List<QualityGate> gates,
      final boolean failFast
  ) {
    return run(project, gates, CheckProgressListener.NO_OP, failFast);
  }

  /**
   * Runs the gates in order, reporting progress to {@code listener} and skipping the rest after the
   * first failure when {@code failFast}.
   */
  public CheckReport run(
      final Project project,
      final List<QualityGate> gates,
      final CheckProgressListener listener,
      final boolean failFast
  ) {
    return run(project, gates, new CheckRunOptions(List.of(), listener, failFast, Set.of()));
  }

  /**
   * Runs the gates, up to {@code options.jobs()} at once, and returns their results in the order of
   * {@code gates}. A gate that is not {@linkplain QualityGate#independent() independent} runs after
   * every such gate before it, so those run one at a time in order. The report and the progress
   * events do not depend on the number of jobs: with fail-fast, every gate after the first one that
   * did not pass is reported as not run, even when it ran. The jobs are one limit for the run: a
   * running gate counts as one, and so does each thread a gate takes from the limit it is given.
   */
  public CheckReport run(
      final Project project,
      final List<QualityGate> gates,
      final CheckRunOptions options
  ) {
    final GateRun run = new GateRun(project, prerequisites.start(), options);
    if (options.jobs() <= SERIAL || gates.size() <= SERIAL) {
      return new CheckReport(project, collect(gates, run, index -> run.execute(gates, index)));
    }
    final int threads = Math.min(options.jobs(), gates.size());
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      try {
        final List<CompletableFuture<GateResult>> started = start(gates, run, pool);
        return new CheckReport(project, collect(gates, run, index -> await(started.get(index))));
      } finally {
        // Closing the pool waits for the running gates; the ones that wait for a thread must not
        // start once the walk has ended.
        run.stopAfter(NONE);
      }
    }
  }

  /** Submits every gate: independent ones at once, the others chained in order. */
  private List<CompletableFuture<GateResult>> start(
      final List<QualityGate> gates,
      final GateRun run,
      final ExecutorService pool
  ) {
    final List<CompletableFuture<GateResult>> started = new ArrayList<>(gates.size());
    CompletableFuture<GateResult> ordered = CompletableFuture.completedFuture(null);
    for (int i = 0; i < gates.size(); i++) {
      final int index = i;
      if (gates.get(i).independent()) {
        started.add(CompletableFuture.supplyAsync(() -> run.execute(gates, index), pool));
      } else {
        ordered = ordered.thenApplyAsync(previous -> run.execute(gates, index), pool);
        started.add(ordered);
      }
    }
    return started;
  }

  /** Walks the gates in order, reporting each one and applying fail-fast. */
  private static List<GateResult> collect(
      final List<QualityGate> gates,
      final GateRun run,
      final IntFunction<GateResult> resultOf
  ) {
    final List<GateResult> results = new ArrayList<>(gates.size());
    for (int i = 0; i < gates.size(); i++) {
      run.progress.started(i, gates.get(i).name());
      final GateResult result = resultOf.apply(i);
      results.add(result);
      run.progress.finished(result);
      if (run.options.failFast() && result.status() != GateStatus.PASSED) {
        run.stopAfter(i);
        gates.subList(i + 1, gates.size()).forEach(skipped -> results.add(run.skip(skipped)));
        break;
      }
    }
    return results;
  }

  private static GateResult await(final CompletableFuture<GateResult> result) {
    try {
      return result.join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      throw exception;
    }
  }

  /** The state of one run, shared by the threads that execute its gates. */
  private final class GateRun {
    private final Project project;
    private final GatePrerequisites.RunPrerequisites checks;
    private final CheckRunOptions options;
    private final OrderedProgress progress;
    private final WorkLimit limit;
    private final AtomicInteger lastToRun = new AtomicInteger(Integer.MAX_VALUE);

    GateRun(
        final Project project,
        final GatePrerequisites.RunPrerequisites checks,
        final CheckRunOptions options
    ) {
      this.project = project;
      this.checks = checks;
      this.options = options;
      this.progress = new OrderedProgress(options.listener());
      this.limit = new WorkLimit(Math.max(SERIAL, options.jobs()));
    }

    /** Keeps gates after {@code index} that have not started from starting. */
    void stopAfter(final int index) {
      lastToRun.accumulateAndGet(index, Math::min);
    }

    /** Runs the gate at {@code index}; returns {@code null} when the run stopped before it. */
    GateResult execute(final List<QualityGate> gates, final int index) {
      if (index > lastToRun.get()) {
        return null;
      }
      final QualityGate gate = gates.get(index);
      final long start = System.nanoTime();
      final Instant gateStart = Instant.now();
      LOGGER.log(Level.INFO, () -> "event=gate-start gate=" + gate.name());
      final GateResult result =
          withFindings(
              project,
              checks.attach(
                  project,
                  limit.holding(
                      () ->
                          gate.execute(
                              project,
                              step -> progress.step(index, gate.name(), step),
                              limit
                          )
                  )
              ),
              gateStart
          );
      LOGGER.log(
          Level.INFO,
          () ->
              "event=gate-complete gate="
                  + gate.name()
                  + " status="
                  + result.status()
                  + " duration-ms="
                  + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
      );
      if (options.failFast() && result.status() != GateStatus.PASSED) {
        stopAfter(index);
      }
      return result;
    }

    GateResult skip(final QualityGate gate) {
      final GateResult skipped = skippedByFailFast(gate);
      progress.finished(skipped);
      LOGGER.log(Level.INFO, () -> "event=gate-skip gate=" + gate.name() + " reason=fail-fast");
      return skipped;
    }
  }

  /**
   * Attaches findings to a failed gate that does not carry its own. Extraction problems never
   * change the verdict: they are logged and the gate keeps no findings.
   */
  private GateResult withFindings(
      final Project project,
      final GateResult result,
      final Instant gateStart
  ) {
    if (result.status() != GateStatus.FAILED || !result.findings().isEmpty()) {
      return result;
    }
    try {
      final GateFindings findings =
          GateFindings.of(findingExtractor.extract(project, result, gateStart));
      LOGGER.log(
          Level.INFO,
          () ->
              "event=findings-attached gate="
                  + result.name()
                  + " total="
                  + findings.total()
                  + " truncated="
                  + findings.truncated()
      );
      return result.withFindings(findings);
    } catch (FindingExtractionException exception) {
      LOGGER.log(
          Level.WARNING,
          () ->
              "event=findings-failed gate="
                  + result.name()
                  + " error="
                  + exception.getClass().getSimpleName()
      );
      return result;
    }
  }

  private static GateResult skippedByFailFast(final QualityGate gate) {
    return GateResult.builder(gate.name(), GateStatus.SKIPPED)
        .command(List.of())
        .exitCode(0)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr("")
        .summary("not run (fail-fast)")
        .build();
  }
}
