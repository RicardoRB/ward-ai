package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/** Reads mypy diagnostics from the {@code -O json} lines printed on standard output. */
public final class MypyJsonFindings implements GateFindingSource {
  static final String GATE = "mypy";
  private static final Logger LOGGER = Logger.getLogger(MypyJsonFindings.class.getName());
  private static final String NOTE = "note";

  private final JsonCodec codec;

  /** Creates the adapter that decodes mypy output with {@code codec}. */
  @Inject
  public MypyJsonFindings(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final String stdout = result.stdout();
    if (stdout == null) {
      return List.of();
    }
    return stdout
        .lines()
        .filter(line -> line.startsWith("{"))
        .map(this::diagnostic)
        .flatMap(Optional::stream)
        .filter(diagnostic -> !NOTE.equals(diagnostic.severity))
        .map(diagnostic -> finding(project, diagnostic))
        .toList();
  }

  private Optional<MypyDiagnostic> diagnostic(final String line) {
    try {
      return Optional.ofNullable(codec.fromJson(line, MypyDiagnostic.class));
    } catch (JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(Level.FINE, () -> "event=findings-line-skipped gate=" + GATE);
      return Optional.empty();
    }
  }

  private static Finding finding(final Project project, final MypyDiagnostic diagnostic) {
    // Lines without an error code, such as some plugin messages, get the catch-all rule.
    final String rule =
        diagnostic.code == null || diagnostic.code.isBlank() ? GATE : diagnostic.code.strip();
    return new Finding(
        GATE,
        GATE,
        rule,
        FindingSeverity.ERROR,
        diagnostic.message == null || diagnostic.message.isBlank()
            ? "mypy diagnostic."
            : diagnostic.message.strip(),
        ReportFiles.relativize(project, diagnostic.file),
        positive(diagnostic.line),
        // mypy's JSON column is 0-based, unlike its text output.
        diagnostic.column == null ? null : positive(diagnostic.column + 1),
        null);
  }

  private static Integer positive(final Integer value) {
    return value == null || value < 1 ? null : value;
  }

  @JsonType
  static final class MypyDiagnostic {
    String file;
    Integer line;
    Integer column;
    String message;
    String code;
    String severity;
  }
}
