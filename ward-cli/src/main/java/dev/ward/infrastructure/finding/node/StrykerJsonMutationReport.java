package dev.ward.infrastructure.finding.node;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.infrastructure.finding.MutationElementsReport;
import dev.ward.infrastructure.finding.ReportFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;

/** Reads mutant statuses from Stryker's JSON report at {@code reports/mutation/mutation.json}. */
public final class StrykerJsonMutationReport implements MutationReport {
  static final String REPORT = "reports/mutation/mutation.json";

  private final JsonCodec codec;
  private final WorkspaceMembers members;

  /** Creates the adapter that decodes Stryker reports with {@code codec}. */
  @Inject
  public StrykerJsonMutationReport(final JsonCodec codec, final WorkspaceMembers members) {
    this.codec = codec;
    this.members = members;
  }

  /** Creates the adapter for a single project, without workspace members. */
  public StrykerJsonMutationReport(final JsonCodec codec) {
    this(codec, WorkspaceMembers.none());
  }

  @Override
  public MutationCounts read(final Project project, final Instant since) {
    return project.taskRunner().isRootRunner()
        ? readMembers(project, since)
        : readOne(project.root(), since);
  }

  /** Adds up the reports of every member that has one. */
  private MutationCounts readMembers(final Project project, final Instant since) {
    final List<Path> reports =
        members.members(project.root()).stream()
            .map(WorkspaceMember::project)
            .map(Project::root)
            .map(root -> root.resolve(REPORT))
            .filter(Files::isRegularFile)
            .toList();
    if (reports.isEmpty()) {
      return MutationCounts.unusable("Stryker's JSON report " + REPORT + " is missing.");
    }
    final List<Path> fresh = ReportFiles.fresh(SupportedQualityGates.STRYKER, since, reports);
    if (fresh.isEmpty()) {
      return MutationCounts.unusable(
          "Stryker's JSON report " + REPORT + " was written before the gate started."
      );
    }
    int detected = 0;
    int undetected = 0;
    for (final Path report : fresh) {
      final MutationCounts counts = readReport(project.root(), report);
      if (!counts.usable()) {
        return counts;
      }
      detected += counts.detected();
      undetected += counts.undetected();
    }
    return MutationCounts.of(detected, undetected);
  }

  private MutationCounts readOne(final Path root, final Instant since) {
    final Path report = root.resolve(REPORT);
    if (!Files.isRegularFile(report)) {
      return MutationCounts.unusable("Stryker's JSON report " + REPORT + " is missing.");
    }
    if (ReportFiles.fresh(SupportedQualityGates.STRYKER, since, List.of(report)).isEmpty()) {
      return MutationCounts.unusable(
          "Stryker's JSON report " + REPORT + " was written before the gate started."
      );
    }
    return readReport(root, report);
  }

  private MutationCounts readReport(final Path root, final Path report) {
    return MutationElementsReport.read(
        codec,
        root,
        report,
        SupportedQualityGates.STRYKER,
        "Stryker's JSON report " + REPORT + " cannot be parsed."
    );
  }
}
