package dev.ward.infrastructure.finding.rust;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.finding.ReportFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads the result lists that {@code cargo mutants} writes under {@code mutants.out}: {@code
 * caught.txt} and {@code timeout.txt} hold the detected mutants, {@code missed.txt} the survivors,
 * one mutant per line, and {@code unviable.txt} the mutants that do not compile, which do not
 * count.
 */
public final class CargoMutantsReport implements MutationReport {
  static final String RESULTS = "mutants.out";
  private static final Logger LOGGER = Logger.getLogger(CargoMutantsReport.class.getName());
  private static final String CAUGHT = "caught.txt";
  private static final String TIMEOUT = "timeout.txt";
  private static final String MISSED = "missed.txt";

  /** Creates the stateless reader that Dagger injects. */
  @Inject
  public CargoMutantsReport() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public MutationCounts read(final Project project, final Instant since) {
    final Path directory = project.root().resolve(RESULTS);
    final Path caught = directory.resolve(CAUGHT);
    if (!Files.isRegularFile(caught)) {
      return MutationCounts.unusable("cargo-mutants' results under " + RESULTS + "/ are missing.");
    }
    if (ReportFiles.fresh(SupportedQualityGates.CARGO_MUTANTS, since, List.of(caught)).isEmpty()) {
      return MutationCounts.unusable(
          "cargo-mutants' results under " + RESULTS + "/ were written before the gate started."
      );
    }
    try {
      final List<String> missed = lines(directory.resolve(MISSED));
      final int detected = lines(caught).size() + lines(directory.resolve(TIMEOUT)).size();
      return MutationCounts.of(detected, missed.size(), missed);
    } catch (IOException exception) {
      LOGGER.log(
          Level.INFO,
          () -> "event=findings-parse-failed gate=" + SupportedQualityGates.CARGO_MUTANTS
      );
      return MutationCounts.unusable(
          "cargo-mutants' results under " + RESULTS + "/ cannot be read."
      );
    }
  }

  /** The non-blank lines of {@code file}, or none when cargo-mutants wrote no such file. */
  private static List<String> lines(final Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    return Files.readAllLines(file).stream().map(String::strip).filter(line -> !line.isEmpty()).toList();
  }
}
