package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateProgress;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Gates, options and listeners for tests of gates that run at the same time. */
final class ParallelGateFixtures {
  private static final long WAIT_SECONDS = 10;

  private ParallelGateFixtures() {}

  /** A gate that runs {@code body} and then ends with {@code status}. */
  static QualityGate gate(
      final String name,
      final GateStatus status,
      final boolean independent,
      final Runnable body
  ) {
    return new QualityGate() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean independent() {
        return independent;
      }

      @Override
      public GateResult execute(final Project project) {
        return execute(project, step -> {});
      }

      @Override
      public GateResult execute(final Project project, final GateProgress progress) {
        progress.step("working");
        body.run();
        return GateResult.builder(name, status)
            .command(List.of(name))
            .exitCode(status == GateStatus.PASSED ? 0 : 1)
            .duration(Duration.ZERO)
            .stdout("")
            .stderr("")
            .build();
      }
    };
  }

  static void await(final CountDownLatch latch) {
    try {
      assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  static CheckRunOptions options(
      final CheckProgressListener listener,
      final boolean failFast,
      final int jobs
  ) {
    return new CheckRunOptions(List.of(), listener, failFast, Set.of(), jobs);
  }

  static CheckProgressListener recording(final List<String> events) {
    return new CheckProgressListener() {
      @Override
      public void gateStarted(final String name) {
        events.add("start:" + name);
      }

      @Override
      public void gateStep(final String name, final String step) {
        events.add("step:" + name);
      }

      @Override
      public void gateFinished(final GateResult result) {
        events.add("end:" + result.name() + ":" + result.status());
      }
    };
  }

  static List<String> withoutSteps(final List<String> events) {
    return events.stream().filter(event -> !event.startsWith("step:")).toList();
  }
}
