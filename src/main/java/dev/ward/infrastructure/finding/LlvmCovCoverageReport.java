package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/**
 * Reads production line coverage from the llvm-cov export whose path {@code swift test
 * --show-codecov-path} prints, leaving out tests, build products and Ward's own files.
 */
public final class LlvmCovCoverageReport implements CoverageReport {
  private static final Logger LOGGER = Logger.getLogger(LlvmCovCoverageReport.class.getName());
  private static final List<String> EXCLUDED_DIRECTORIES = List.of(".build/", ".ward/");
  private static final String TESTS = "Tests";
  private static final String RUN_TESTS_FIRST =
      " Run the tests with `swift test --enable-code-coverage` first; a failing test writes no"
          + " export.";

  private final JsonCodec codec;

  /** Creates the adapter that decodes llvm-cov exports with {@code codec}. */
  @Inject
  public LlvmCovCoverageReport(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public LineCoverage read(final Project project, final String stdout) {
    final String reported =
        Objects.toString(stdout, "")
            .lines()
            .filter(line -> !line.isBlank())
            .reduce((a, b) -> b)
            .orElse("")
            .strip();
    if (reported.isEmpty()) {
      return LineCoverage.unusable("The command printed no llvm-cov export path.");
    }
    final Path export;
    try {
      export = project.root().resolve(reported);
    } catch (InvalidPathException exception) {
      return LineCoverage.unusable("The command printed no llvm-cov export path.");
    }
    if (!Files.isRegularFile(export)) {
      return LineCoverage.unusable(
          "The llvm-cov export "
              + ReportFiles.relativize(project, reported)
              + " is missing."
              + RUN_TESTS_FIRST);
    }
    return read(project, export);
  }

  private LineCoverage read(final Project project, final Path export) {
    final Export decoded;
    try {
      decoded = codec.fromJson(Files.readString(export), Export.class);
    } catch (IOException | JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(
          Level.INFO, "event=findings-parse-failed gate=" + SupportedQualityGates.SWIFT_COVERAGE);
      return LineCoverage.unusable("The llvm-cov export cannot be parsed.");
    }
    final List<ExportFile> production =
        decoded == null
            ? List.of()
            : decoded.filesOrEmpty().stream()
                .filter(file -> isProduction(ReportFiles.relativize(project, file.filename)))
                .toList();
    final long covered = production.stream().mapToLong(ExportFile::covered).sum();
    final long executable = production.stream().mapToLong(ExportFile::count).sum();
    if (executable == 0) {
      return LineCoverage.unusable("The llvm-cov export has no executable production lines.");
    }
    return LineCoverage.of(covered, executable);
  }

  /** Leaves out build products, Ward's files and every directory named or ending in Tests. */
  private static boolean isProduction(final String file) {
    if (file == null || EXCLUDED_DIRECTORIES.stream().anyMatch(file::startsWith)) {
      return false;
    }
    final String[] segments = file.split("/");
    return Arrays.stream(segments, 0, segments.length - 1)
        .noneMatch(segment -> segment.endsWith(TESTS));
  }

  @JsonType
  static final class Export {
    List<ExportData> data;

    List<ExportFile> filesOrEmpty() {
      return data == null
          ? List.of()
          : data.stream()
              .filter(Objects::nonNull)
              .filter(entry -> entry.files != null)
              .flatMap(entry -> entry.files.stream())
              .filter(Objects::nonNull)
              .toList();
    }
  }

  @JsonType
  static final class ExportData {
    List<ExportFile> files;
  }

  @JsonType
  static final class ExportFile {
    String filename;
    ExportSummary summary;

    long covered() {
      return summary == null || summary.lines == null ? 0 : summary.lines.coveredOrZero();
    }

    long count() {
      return summary == null || summary.lines == null ? 0 : summary.lines.countOrZero();
    }
  }

  @JsonType
  static final class ExportSummary {
    ExportLines lines;
  }

  @JsonType
  static final class ExportLines {
    Long count;
    Long covered;

    long countOrZero() {
      return count == null ? 0 : count;
    }

    long coveredOrZero() {
      return covered == null ? 0 : covered;
    }
  }
}
