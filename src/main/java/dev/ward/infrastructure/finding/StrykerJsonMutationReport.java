package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/** Reads mutant statuses from Stryker's JSON report at {@code reports/mutation/mutation.json}. */
public final class StrykerJsonMutationReport implements MutationReport {
  static final String REPORT = "reports/mutation/mutation.json";
  private static final Logger LOGGER = Logger.getLogger(StrykerJsonMutationReport.class.getName());
  private static final Set<String> DETECTED = Set.of("Killed", "Timeout");
  private static final Set<String> UNDETECTED = Set.of("Survived", "NoCoverage");
  private static final Set<String> EXCLUDED =
      Set.of("Ignored", "Pending", "CompileError", "RuntimeError");

  private final JsonCodec codec;

  /** Creates the adapter that decodes Stryker reports with {@code codec}. */
  @Inject
  public StrykerJsonMutationReport(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public MutationCounts read(final Project project, final Instant since) {
    final Path report = project.root().resolve(REPORT);
    if (!Files.isRegularFile(report)) {
      return MutationCounts.unusable("Stryker's JSON report " + REPORT + " is missing.");
    }
    if (ReportFiles.fresh(SupportedQualityGates.STRYKER, since, List.of(report)).isEmpty()) {
      return MutationCounts.unusable(
          "Stryker's JSON report " + REPORT + " was written before the gate started.");
    }
    final StrykerReport decoded;
    try {
      decoded = codec.fromJson(Files.readString(report), StrykerReport.class);
    } catch (IOException | JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(Level.INFO, "event=findings-parse-failed gate=" + SupportedQualityGates.STRYKER);
      return MutationCounts.unusable("Stryker's JSON report " + REPORT + " cannot be parsed.");
    }
    return counts(decoded == null ? List.of() : decoded.statuses());
  }

  private static MutationCounts counts(final List<String> statuses) {
    final long unknown =
        statuses.stream()
            .filter(
                status ->
                    !DETECTED.contains(status)
                        && !UNDETECTED.contains(status)
                        && !EXCLUDED.contains(status))
            .count();
    if (unknown > 0) {
      LOGGER.log(Level.FINE, () -> "event=stryker-status-unknown count=" + unknown);
    }
    return MutationCounts.of(
        (int) statuses.stream().filter(DETECTED::contains).count(),
        (int) statuses.stream().filter(UNDETECTED::contains).count());
  }

  @JsonType
  static final class StrykerReport {
    Map<String, StrykerFile> files;

    List<String> statuses() {
      if (files == null) {
        return List.of();
      }
      return files.values().stream()
          .filter(Objects::nonNull)
          .flatMap(file -> file.mutantsOrEmpty().stream())
          .map(mutant -> mutant.status)
          .filter(Objects::nonNull)
          .toList();
    }
  }

  @JsonType
  static final class StrykerFile {
    List<StrykerMutant> mutants;

    List<StrykerMutant> mutantsOrEmpty() {
      return mutants == null ? List.of() : mutants.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class StrykerMutant {
    String status;
  }
}
