package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.FindingExtractionException;
import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.gate.ToolReports;
import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads PIT's {@code mutations.xml}. Each mutation's {@code detected} attribute is PIT's own
 * classification, and PIT scores detected mutations against all of them.
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
    final Path report = project.root().resolve(relative);
    if (!Files.isRegularFile(report)) {
      return MutationCounts.unusable("PIT's XML report " + relative + " is missing.");
    }
    if (ReportFiles.fresh(SupportedQualityGates.MUTATION, since, List.of(report)).isEmpty()) {
      return MutationCounts.unusable(
          "PIT's XML report " + relative + " was written before the gate started.");
    }
    final int[] counts = new int[2];
    try {
      SafeXml.read(
          report,
          reader -> {
            if (MUTATION_ELEMENT.equals(reader.getLocalName())) {
              counts[Boolean.parseBoolean(reader.getAttributeValue(null, "detected")) ? 0 : 1]++;
            }
          });
    } catch (FindingExtractionException exception) {
      LOGGER.log(Level.INFO, "event=findings-parse-failed gate=" + SupportedQualityGates.MUTATION);
      return MutationCounts.unusable("PIT's XML report " + relative + " cannot be parsed.");
    }
    return MutationCounts.of(counts[0], counts[1]);
  }
}
