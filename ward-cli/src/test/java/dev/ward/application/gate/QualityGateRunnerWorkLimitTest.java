package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.port.WorkLimit;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateProgress;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The jobs of a run are one limit: the gates and the threads a gate takes are counted together. */
class QualityGateRunnerWorkLimitTest {
  private static final int JOBS = 3;
  private static final int ITEMS = 300;

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private final QualityGateRunner runner = new QualityGateRunner();
  private final AtomicInteger working = new AtomicInteger();
  private final AtomicInteger most = new AtomicInteger();
  private final List<Integer> limits = new CopyOnWriteArrayList<>();

  @Test
  void gatesAndTheirThreadsNeverWorkOnMoreThreadsThanTheJobs() {
    final CheckReport report =
        runner.run(
            project,
            List.of(new MappingGate("a"), new MappingGate("b"), new MappingGate("c")),
            ParallelGateFixtures.options(CheckProgressListener.NO_OP, false, JOBS)
        );

    assertThat(report.passed()).isTrue();
    assertThat(limits).containsExactly(JOBS, JOBS, JOBS);
    assertThat(most.get()).isBetween(1, JOBS);
  }

  @Test
  void withoutJobsAGateGetsALimitOfOneAndCanStillUseIt() {
    final CheckReport report = runner.run(project, List.of(new MappingGate("a")));

    assertThat(report.passed()).isTrue();
    assertThat(limits).containsExactly(1);
    assertThat(most.get()).isEqualTo(1);
  }

  /** An independent gate that does its work through the limit, as the {@code ward} gate does. */
  private final class MappingGate implements QualityGate {
    private final String name;

    MappingGate(final String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean independent() {
      return true;
    }

    @Override
    public GateResult execute(final Project checked) {
      return execute(checked, GateProgress.NONE, WorkLimit.serial());
    }

    @Override
    public GateResult execute(
        final Project checked,
        final GateProgress progress,
        final WorkLimit limit
    ) {
      limits.add(limit.threads());
      limit.map(
          IntStream.range(0, ITEMS).boxed().toList(),
          item -> {
            most.accumulateAndGet(working.incrementAndGet(), Math::max);
            Thread.onSpinWait();
            return working.decrementAndGet();
          }
      );
      return GateResult.builder(name, GateStatus.PASSED)
          .command(List.of())
          .exitCode(0)
          .duration(Duration.ZERO)
          .stdout("")
          .stderr("")
          .summary("ok")
          .build();
    }
  }
}
