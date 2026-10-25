package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.xml.stream.XMLStreamReader;

/** Reads Biome findings from the checkstyle XML printed on standard output. */
public final class BiomeCheckstyleFindings implements GateFindingSource {
  static final String GATE = "biome";
  private static final Logger LOGGER = Logger.getLogger(BiomeCheckstyleFindings.class.getName());
  // Package managers may print lines before the report, so it starts at its XML declaration.
  private static final String DECLARATION = "<?xml";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public BiomeCheckstyleFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final String stdout = result.stdout();
    final int start = stdout == null ? -1 : stdout.indexOf(DECLARATION);
    if (start < 0) {
      LOGGER.log(Level.INFO, () -> "event=findings-parse-failed gate=" + GATE);
      return List.of();
    }
    final List<Finding> findings = new ArrayList<>();
    final String[] file = new String[1];
    try {
      SafeXml.read(stdout.substring(start), reader -> visit(project, reader, file, findings));
    } catch (MalformedReportException exception) {
      LOGGER.log(Level.INFO, () -> "event=findings-parse-failed gate=" + GATE);
      return List.of();
    }
    return findings;
  }

  private static void visit(
      final Project project,
      final XMLStreamReader reader,
      final String[] file,
      final List<Finding> into) {
    switch (reader.getLocalName()) {
      case "file" -> {
        file[0] = ReportFiles.relativize(project, reader.getAttributeValue(null, "name"));
      }
      case "error" -> finding(reader, file[0]).ifPresent(into::add);
      default -> {
        // Other elements carry no diagnostic.
      }
    }
  }

  private static Optional<Finding> finding(final XMLStreamReader reader, final String file) {
    final Optional<FindingSeverity> severity = severity(reader.getAttributeValue(null, "severity"));
    if (severity.isEmpty()) {
      return Optional.empty();
    }
    final String source = reader.getAttributeValue(null, "source");
    final String message = reader.getAttributeValue(null, "message");
    return Optional.of(
        new Finding(
            GATE,
            GATE,
            source == null || source.isBlank() ? GATE : source.strip(),
            severity.get(),
            message == null || message.isBlank() ? "Biome problem." : message.strip(),
            file,
            SafeXml.intAttribute(reader, "line"),
            SafeXml.intAttribute(reader, "column"),
            null));
  }

  private static Optional<FindingSeverity> severity(final String value) {
    return switch (value == null ? "" : value) {
      case "error" -> Optional.of(FindingSeverity.ERROR);
      case "warning" -> Optional.of(FindingSeverity.WARNING);
      default -> Optional.empty();
    };
  }
}
