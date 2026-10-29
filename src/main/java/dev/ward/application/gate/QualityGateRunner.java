package dev.ward.application.gate;

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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Executes gates in order and aggregates their results. A failing gate does not stop the others.
 */
public class QualityGateRunner {
  private static final Logger LOGGER = Logger.getLogger(QualityGateRunner.class.getName());

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
      final FindingExtractor findingExtractor, final GatePrerequisites prerequisites) {
    this.findingExtractor = findingExtractor;
    this.prerequisites = prerequisites;
  }

  /** Runs every gate in order, without progress output. */
  public CheckReport run(final Project project, final List<QualityGate> gates) {
    return run(project, gates, CheckProgressListener.NO_OP, false);
  }

  /** Runs every gate in order, reporting progress to {@code listener}. */
  public CheckReport run(
      final Project project, final List<QualityGate> gates, final CheckProgressListener listener) {
    return run(project, gates, listener, false);
  }

  /** Runs the gates in order, skipping the rest after the first failure when {@code failFast}. */
  public CheckReport run(
      final Project project, final List<QualityGate> gates, final boolean failFast) {
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
      final boolean failFast) {
    final List<GateResult> results = new ArrayList<>();
    final GatePrerequisites.RunPrerequisites checks = prerequisites.start();
    for (int i = 0; i < gates.size(); i++) {
      final QualityGate gate = gates.get(i);
      listener.gateStarted(gate.name());
      final long start = System.nanoTime();
      final Instant gateStart = Instant.now();
      LOGGER.log(Level.INFO, () -> "event=gate-start gate=" + gate.name());
      final GateResult result =
          withFindings(project, checks.attach(project, gate.execute(project)), gateStart);
      results.add(result);
      listener.gateFinished(result);
      LOGGER.log(
          Level.INFO,
          () ->
              "event=gate-complete gate="
                  + gate.name()
                  + " status="
                  + result.status()
                  + " duration-ms="
                  + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      if (failFast && result.status() != GateStatus.PASSED) {
        for (final QualityGate skipped : gates.subList(i + 1, gates.size())) {
          final GateResult skippedResult = skippedByFailFast(skipped);
          results.add(skippedResult);
          listener.gateFinished(skippedResult);
          LOGGER.log(
              Level.INFO, () -> "event=gate-skip gate=" + skipped.name() + " reason=fail-fast");
        }
        break;
      }
    }
    return new CheckReport(project, results);
  }

  /**
   * Attaches findings to a failed gate. Extraction problems never change the verdict: they are
   * logged and the gate keeps no findings.
   */
  private GateResult withFindings(
      final Project project, final GateResult result, final Instant gateStart) {
    if (result.status() != GateStatus.FAILED) {
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
                  + findings.truncated());
      return result.withFindings(findings);
    } catch (FindingExtractionException exception) {
      LOGGER.log(
          Level.WARNING,
          () ->
              "event=findings-failed gate="
                  + result.name()
                  + " error="
                  + exception.getClass().getSimpleName());
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
