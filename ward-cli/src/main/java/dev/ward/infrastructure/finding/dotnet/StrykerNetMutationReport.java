package dev.ward.infrastructure.finding.dotnet;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.finding.MutationElementsReport;
import dev.ward.infrastructure.finding.ReportFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;

/**
 * Reads mutant statuses from the JSON report that Stryker.NET writes below its {@code --output}
 * directory, which init sets to {@code .ward/stryker}.
 */
public final class StrykerNetMutationReport implements MutationReport {
  /** The report, relative to the project root. */
  public static final String REPORT = ".ward/stryker/reports/mutation-report.json";

  private final JsonCodec codec;

  /** Creates the adapter that decodes Stryker.NET reports with {@code codec}. */
  @Inject
  public StrykerNetMutationReport(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public MutationCounts read(final Project project, final Instant since) {
    final Path report = project.root().resolve(REPORT);
    if (!Files.isRegularFile(report)) {
      return MutationCounts.unusable("Stryker.NET's JSON report " + REPORT + " is missing.");
    }
    if (ReportFiles.fresh(SupportedQualityGates.STRYKER_NET, since, List.of(report)).isEmpty()) {
      return MutationCounts.unusable(
          "Stryker.NET's JSON report " + REPORT + " was written before the gate started."
      );
    }
    return MutationElementsReport.read(
        codec,
        project.root(),
        report,
        SupportedQualityGates.STRYKER_NET,
        "Stryker.NET's JSON report " + REPORT + " cannot be parsed."
    );
  }
}
