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
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/**
 * Reads mutmut 3's per-file results under {@code mutants/}. Each {@code .meta} file maps mutant
 * keys to the exit code of their run; the exit code gives the mutant's status as mutmut's own
 * {@code mutmut/stats.py} does.
 */
public final class MutmutMetaMutationReport implements MutationReport {
  static final String RESULTS = "mutants";
  private static final String META_SUFFIX = ".meta";
  private static final Logger LOGGER = Logger.getLogger(MutmutMetaMutationReport.class.getName());
  // Killed, timed out and caught by type check: mutmut's detected statuses.
  private static final Set<Integer> DETECTED = Set.of(1, 3, 24, 36, -24, 37, 152, 255);
  // Survived and no tests: mutmut's undetected statuses.
  private static final Set<Integer> UNDETECTED = Set.of(0, 5, 33);

  private final JsonCodec codec;

  /** Creates the adapter that decodes mutmut's result files with {@code codec}. */
  @Inject
  public MutmutMetaMutationReport(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public MutationCounts read(final Project project, final Instant since) {
    final Path directory = project.root().resolve(RESULTS);
    final List<Path> files = metaFiles(directory);
    if (files.isEmpty()) {
      return MutationCounts.unusable("mutmut's results under " + RESULTS + "/ are missing.");
    }
    if (ReportFiles.fresh(SupportedQualityGates.MUTMUT, since, files).isEmpty()) {
      return MutationCounts.unusable(
          "mutmut's results under " + RESULTS + "/ were written before the gate started.");
    }
    int detected = 0;
    int undetected = 0;
    for (final Path file : files) {
      final List<Integer> exitCodes;
      try {
        exitCodes = exitCodes(file);
      } catch (IOException | JsonCodecException | IllegalArgumentException exception) {
        LOGGER.log(Level.INFO, "event=findings-parse-failed gate=" + SupportedQualityGates.MUTMUT);
        return MutationCounts.unusable(
            "mutmut's result file "
                + RESULTS
                + "/"
                + directory.relativize(file)
                + " cannot be parsed.");
      }
      detected += (int) exitCodes.stream().filter(DETECTED::contains).count();
      undetected += (int) exitCodes.stream().filter(UNDETECTED::contains).count();
    }
    return MutationCounts.of(detected, undetected);
  }

  private static List<Path> metaFiles(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths
          .filter(path -> path.toString().endsWith(META_SUFFIX))
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
    } catch (IOException exception) {
      LOGGER.log(
          Level.INFO, "event=mutmut-results-unreadable gate=" + SupportedQualityGates.MUTMUT);
      return List.of();
    }
  }

  /** Returns the recorded exit codes of one file's mutants, leaving out mutants with none. */
  private List<Integer> exitCodes(final Path file) throws IOException {
    final MutmutMeta meta = codec.fromJson(Files.readString(file), MutmutMeta.class);
    if (meta == null || meta.exitCodeByKey == null) {
      return List.of();
    }
    return meta.exitCodeByKey.values().stream().filter(Objects::nonNull).toList();
  }

  @JsonType
  static final class MutmutMeta {
    @JsonProperty("exit_code_by_key")
    Map<String, Integer> exitCodeByKey;
  }
}
