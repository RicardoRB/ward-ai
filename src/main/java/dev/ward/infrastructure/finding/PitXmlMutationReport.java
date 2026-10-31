package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.FindingExtractionException;
import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.gate.ToolReports;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.ProjectDirectories;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads PIT's {@code mutations.xml}. Each mutation's {@code detected} attribute is PIT's own
 * classification, and PIT scores detected mutations against all of them. A Gradle build sums the
 * fresh reports of the root and of every module below it.
 */
public final class PitXmlMutationReport implements MutationReport {
  private static final Logger LOGGER = Logger.getLogger(PitXmlMutationReport.class.getName());
  private static final String MUTATION_ELEMENT = "mutation";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public PitXmlMutationReport() {}

  @Override
  public MutationCounts read(final Project project, final Instant since) {
    final String relative = ToolReports.pitMutations(project);
    final List<Path> present = candidates(project).stream().filter(Files::isRegularFile).toList();
    if (present.isEmpty()) {
      return MutationCounts.unusable("PIT's XML report " + relative + " is missing.");
    }
    final List<Path> fresh = ReportFiles.fresh(SupportedQualityGates.MUTATION, since, present);
    if (fresh.isEmpty()) {
      return MutationCounts.unusable(
          "PIT's XML report " + relative + " was written before the gate started.");
    }
    int detected = 0;
    int undetected = 0;
    for (final Path report : fresh) {
      try {
        final int[] counts = count(report);
        detected += counts[0];
        undetected += counts[1];
      } catch (FindingExtractionException exception) {
        LOGGER.log(
            Level.INFO, "event=findings-parse-failed gate=" + SupportedQualityGates.MUTATION);
        return MutationCounts.unusable(
            "PIT's XML report "
                + ReportFiles.relativize(project, report.toString())
                + " cannot be parsed.");
      }
    }
    return MutationCounts.of(detected, undetected);
  }

  /** The report of a Maven build, or the report of the root and of each Gradle module. */
  private static List<Path> candidates(final Project project) {
    final String relative = ToolReports.pitMutations(project);
    return project.buildTool() == BuildTool.GRADLE
        ? ProjectDirectories.below(project.root()).stream()
            .map(dir -> dir.resolve(relative))
            .toList()
        : List.of(project.root().resolve(relative));
  }

  /** Returns detected and undetected mutation counts, in that order. */
  private static int[] count(final Path report) {
    final int[] counts = new int[2];
    SafeXml.read(
        report,
        reader -> {
          if (MUTATION_ELEMENT.equals(reader.getLocalName())) {
            counts[Boolean.parseBoolean(reader.getAttributeValue(null, "detected")) ? 0 : 1]++;
          }
        });
    return counts;
  }
}
