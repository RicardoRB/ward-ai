package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.fory.json.annotation.JsonType;

/**
 * Decodes a report in the mutation-testing-elements JSON format, which Stryker (JavaScript and
 * .NET) writes, into mutant counts and the places of the undetected mutants.
 */
public final class MutationElementsReport {
  private static final Logger LOGGER = Logger.getLogger(MutationElementsReport.class.getName());
  private static final Set<String> DETECTED = Set.of("Killed", "Timeout");
  private static final Set<String> UNDETECTED = Set.of("Survived", "NoCoverage");
  private static final Set<String> EXCLUDED =
      Set.of("Ignored", "Pending", "CompileError", "RuntimeError");

  private MutationElementsReport() {}

  /**
   * Reads {@code report}, or says why it cannot be parsed; {@code gate} names the log event and
   * survivors are named relative to {@code root}.
   */
  public static MutationCounts read(
      final JsonCodec codec,
      final Path root,
      final Path report,
      final String gate,
      final String unparsable
  ) {
    final Elements decoded;
    try {
      decoded = codec.fromJson(Files.readString(report), Elements.class);
    } catch (IOException | JsonCodecException | IllegalArgumentException exception) {
      if (LOGGER.isLoggable(Level.INFO)) {
        LOGGER.log(Level.INFO, "event=findings-parse-failed gate=" + gate);
      }
      return MutationCounts.unusable(unparsable);
    }
    return counts(decoded == null ? List.of() : decoded.mutants(), root.toAbsolutePath() + "/");
  }

  private static MutationCounts counts(final List<Located> mutants, final String base) {
    final long unknown =
        mutants.stream()
            .map(mutant -> mutant.mutant.status)
            .filter(status -> !DETECTED.contains(status) && !UNDETECTED.contains(status))
            .filter(status -> !EXCLUDED.contains(status))
            .count();
    if (unknown > 0) {
      LOGGER.log(Level.FINE, () -> "event=stryker-status-unknown count=" + unknown);
    }
    final List<String> survivors =
        mutants.stream()
            .filter(mutant -> UNDETECTED.contains(mutant.mutant.status))
            .map(mutant -> mutant.describe(base))
            .toList();
    return MutationCounts.of(
        (int)mutants.stream().filter(mutant -> DETECTED.contains(mutant.mutant.status)).count(),
        survivors.size(),
        survivors
    );
  }

  /** A mutant with the file it is in. */
  private record Located(String file, Mutant mutant) {
    /** Names the mutant by file below {@code base} when it is, line and mutator. */
    String describe(final String base) {
      final String name = file.startsWith(base) ? file.substring(base.length()) : file;
      final int line =
          mutant.location == null || mutant.location.start == null ? 0 : mutant.location.start.line;
      return name + ":" + line + " " + Objects.toString(mutant.mutatorName, "mutant");
    }
  }

  @JsonType
  static final class Elements {
    Map<String, ElementsFile> files;

    List<Located> mutants() {
      final List<Located> found = new ArrayList<>();
      if (files != null) {
        files.forEach(
            (name, file) ->
                found.addAll(
                    file == null
                        ? List.of()
                        : file.mutantsOrEmpty().stream()
                            .filter(mutant -> mutant.status != null)
                            .map(mutant -> new Located(name, mutant))
                            .toList()
                )
        );
      }
      return found;
    }
  }

  @JsonType
  static final class ElementsFile {
    List<Mutant> mutants;

    List<Mutant> mutantsOrEmpty() {
      return mutants == null ? List.of() : mutants.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class Mutant {
    String status;
    String mutatorName;
    Location location;
  }

  @JsonType
  static final class Location {
    Position start;
  }

  @JsonType
  static final class Position {
    int line;
  }
}
