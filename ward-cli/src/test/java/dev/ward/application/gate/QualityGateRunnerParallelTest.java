package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QualityGateRunnerParallelTest {
  private static final int JOBS = 4;
  private static final int ONE_AT_A_TIME = 1;

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private final QualityGateRunner runner = new QualityGateRunner();

  @Test
  void independentGatesRunAtTheSameTime() {
    final CountDownLatch bothRunning = new CountDownLatch(2);
    final Runnable meet =
        () -> {
          bothRunning.countDown();
          ParallelGateFixtures.await(bothRunning);
        };

    final CheckReport report =
        runner.run(
            project,
            List.of(
                ParallelGateFixtures.gate("a", GateStatus.PASSED, true, meet),
                ParallelGateFixtures.gate("b", GateStatus.PASSED, true, meet)
            ),
            ParallelGateFixtures.options(CheckProgressListener.NO_OP, false, 2)
        );

    assertThat(report.passed()).isTrue();
  }

  @Test
  void orderedGatesRunOneAtATimeInOrderNextToAnIndependentGate() {
    final AtomicInteger running = new AtomicInteger();
    final AtomicInteger overlaps = new AtomicInteger();
    final List<String> order = new CopyOnWriteArrayList<>();
    final CountDownLatch orderedDone = new CountDownLatch(3);
    final List<QualityGate> gates =
        List.of(
            // Ends only after every ordered gate ended, so it overlaps all of them.
            ParallelGateFixtures.gate(
                "ward",
                GateStatus.PASSED,
                true,
                () -> ParallelGateFixtures.await(orderedDone)
            ),
            ordered("tests", running, overlaps, order, orderedDone),
            ordered("coverage", running, overlaps, order, orderedDone),
            ordered("sbom", running, overlaps, order, orderedDone)
        );

    final CheckReport report =
        runner.run(
            project,
            gates,
            ParallelGateFixtures.options(CheckProgressListener.NO_OP, false, JOBS)
        );

    assertThat(order).containsExactly("tests", "coverage", "sbom");
    assertThat(overlaps).hasValue(0);
    assertThat(report.results())
        .extracting(GateResult::name)
        .containsExactly("ward", "tests", "coverage", "sbom");
  }

  private static QualityGate ordered(
      final String name,
      final AtomicInteger running,
      final AtomicInteger overlaps,
      final List<String> order,
      final CountDownLatch done
  ) {
    return ParallelGateFixtures.gate(
        name,
        GateStatus.PASSED,
        false,
        () -> {
          if (running.incrementAndGet() > ONE_AT_A_TIME) {
            overlaps.incrementAndGet();
          }
          order.add(name);
          Thread.onSpinWait();
          running.decrementAndGet();
          done.countDown();
        }
    );
  }

  @Test
  void eventsAndResultsKeepGateOrderWhenALaterGateEndsFirst() {
    final CountDownLatch laterEnded = new CountDownLatch(1);
    final List<QualityGate> gates =
        List.of(
            ParallelGateFixtures.gate(
                "slow",
                GateStatus.PASSED,
                true,
                () -> ParallelGateFixtures.await(laterEnded)
            ),
            ParallelGateFixtures.gate("fast", GateStatus.FAILED, true, laterEnded::countDown)
        );
    final List<String> parallel = new CopyOnWriteArrayList<>();

    final CheckReport report = runner.run(
        project,
        gates,
        ParallelGateFixtures.options(ParallelGateFixtures.recording(parallel), false, 2)
    );

    assertThat(ParallelGateFixtures.withoutSteps(parallel))
        .containsExactly("start:slow", "end:slow:PASSED", "start:fast", "end:fast:FAILED");
    assertThat(report.results()).extracting(GateResult::name).containsExactly("slow", "fast");
    assertThat(report.passed()).isFalse();
  }

  @Test
  void reportAndEventsAreTheSameForOneAndManyJobs() {
    final List<QualityGate> gates =
        List.of(
            ParallelGateFixtures.gate("format", GateStatus.PASSED, true, () -> {}),
            ParallelGateFixtures.gate("tests", GateStatus.FAILED, false, () -> {}),
            ParallelGateFixtures.gate("secrets", GateStatus.PASSED, true, () -> {}),
            ParallelGateFixtures.gate("coverage", GateStatus.PASSED, false, () -> {})
        );
    final List<String> serialEvents = new CopyOnWriteArrayList<>();
    final List<String> parallelEvents = new CopyOnWriteArrayList<>();

    final CheckReport serial = runner.run(
        project,
        gates,
        ParallelGateFixtures.options(ParallelGateFixtures.recording(serialEvents), false, 1)
    );
    final CheckReport parallel =
        runner.run(
            project,
            gates,
            ParallelGateFixtures.options(
                ParallelGateFixtures.recording(parallelEvents),
                false,
                JOBS
            )
        );

    assertThat(parallel.results()).isEqualTo(serial.results());
    // A step of a gate that is not the reported one is dropped, so compare starts and ends.
    assertThat(ParallelGateFixtures.withoutSteps(parallelEvents)).isEqualTo(
        ParallelGateFixtures.withoutSteps(serialEvents)
    );
  }

  @Test
  void failFastReportsEveryGateAfterTheFirstFailureAsNotRunForAnyJobs() {
    final List<QualityGate> gates =
        List.of(
            ParallelGateFixtures.gate("a", GateStatus.PASSED, true, () -> {}),
            ParallelGateFixtures.gate("b", GateStatus.FAILED, false, () -> {}),
            ParallelGateFixtures.gate("c", GateStatus.FAILED, true, () -> {}),
            ParallelGateFixtures.gate("d", GateStatus.PASSED, false, () -> {})
        );
    final List<String> serialEvents = new CopyOnWriteArrayList<>();
    final List<String> parallelEvents = new CopyOnWriteArrayList<>();

    final CheckReport serial = runner.run(
        project,
        gates,
        ParallelGateFixtures.options(ParallelGateFixtures.recording(serialEvents), true, 1)
    );
    final CheckReport parallel =
        runner.run(
            project,
            gates,
            ParallelGateFixtures.options(ParallelGateFixtures.recording(parallelEvents), true, JOBS)
        );

    assertThat(parallel.results()).isEqualTo(serial.results());
    assertThat(parallel.results())
        .extracting(GateResult::status)
        .containsExactly(
            GateStatus.PASSED,
            GateStatus.FAILED,
            GateStatus.SKIPPED,
            GateStatus.SKIPPED
        );
    assertThat(ParallelGateFixtures.withoutSteps(parallelEvents)).isEqualTo(
        ParallelGateFixtures.withoutSteps(serialEvents)
    );
  }

  @Test
  void failFastDoesNotStartAnOrderedGateAfterAFailedOne() {
    final AtomicInteger started = new AtomicInteger();

    runner.run(
        project,
        List.of(
            ParallelGateFixtures.gate("tests", GateStatus.FAILED, false, () -> {}),
            ParallelGateFixtures.gate("sbom", GateStatus.PASSED, false, started::incrementAndGet)
        ),
        ParallelGateFixtures.options(CheckProgressListener.NO_OP, true, JOBS)
    );

    assertThat(started).hasValue(0);
  }

  @Test
  void aGateErrorIsRethrownAfterTheRunningGatesEnd() {
    final AtomicInteger ended = new AtomicInteger();
    final CountDownLatch failed = new CountDownLatch(1);
    final List<QualityGate> gates =
        List.of(
            ParallelGateFixtures.gate(
                "broken",
                GateStatus.PASSED,
                true,
                () -> {
                  failed.countDown();
                  throw new WardException("broken configuration");
                }
            ),
            ParallelGateFixtures.gate(
                "running",
                GateStatus.PASSED,
                true,
                () -> {
                  ParallelGateFixtures.await(failed);
                  ended.incrementAndGet();
                }
            )
        );

    assertThatThrownBy(
        () -> runner.run(
            project,
            gates,
            ParallelGateFixtures.options(CheckProgressListener.NO_OP, false, 2)
        )
    )
        .isInstanceOf(WardException.class)
        .hasMessage("broken configuration");
    assertThat(ended).hasValue(1);
  }
}
