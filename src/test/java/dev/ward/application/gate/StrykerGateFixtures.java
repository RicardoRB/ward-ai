package dev.ward.application.gate;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs a stryker gate built by the factory against a fake mutation report. */
final class StrykerGateFixtures {
  static final String STRYKER = "stryker";
  static final String STDOUT = "All mutants have been tested. stdout-marker";
  static final List<String> COMMAND =
      List.of("npx", "stryker", "run", "--reporters", "clear-text,progress,json");
  static final int MINIMUM = 60;
  // 5 killed + 1 timeout are detected and 3 survived + 1 no coverage are not: a score of 60.
  static final MutationCounts SIXTY_PERCENT = MutationCounts.of(6, 4);

  private static final Project PROJECT =
      new Project(Path.of("/repo/web"), Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE);

  private StrykerGateFixtures() {}

  /** A report that answers with fixed counts and records each read. */
  static final class FakeReport implements MutationReport {
    final List<Instant> reads = new ArrayList<>();
    private final MutationCounts counts;

    FakeReport(final MutationCounts counts) {
      this.counts = counts;
    }

    @Override
    public MutationCounts read(final Project project, final Instant since) {
      reads.add(since);
      return counts;
    }
  }

  static GateConfiguration gate(final Integer minimum) {
    return new GateConfiguration(true, COMMAND, Set.of("ci"), null, null, null, minimum);
  }

  static GateResult run(final int exitCode, final MutationReport report, final Integer minimum) {
    final QualityGateFactory factory =
        new QualityGateFactory(
            new FakeCommandExecutor(exitCode, STDOUT, ""),
            null,
            new TraceabilitySources(null, null),
            new GateReportPorts(ReportDirectories.NONE, report),
            null);
    return factory
        .create(new WardConfiguration(1, Map.of(STRYKER, gate(minimum))))
        .getFirst()
        .execute(PROJECT);
  }
}
