package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/** Reads Ruff violations from the {@code --output-format json} array printed on standard output. */
public final class RuffJsonFindings implements GateFindingSource {
  static final String GATE = "ruff";
  private static final Logger LOGGER = Logger.getLogger(RuffJsonFindings.class.getName());
  // Package managers may print lines before the tool output, so the array starts at a line's '['.
  private static final Pattern ARRAY_START = Pattern.compile("^\\[", Pattern.MULTILINE);

  private final JsonCodec codec;

  /** Creates the adapter that decodes Ruff output with {@code codec}. */
  @Inject
  public RuffJsonFindings(final JsonCodec codec) {
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
    final Matcher start = ARRAY_START.matcher(stdout);
    if (!start.find()) {
      return List.of();
    }
    final RuffOutput output;
    try {
      output =
          codec.fromJson(
              "{\"violations\":" + stdout.substring(start.start()) + "}", RuffOutput.class);
    } catch (JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(Level.INFO, () -> "event=findings-parse-failed gate=" + GATE);
      return List.of();
    }
    return output.violationsOrEmpty().stream()
        .map(violation -> finding(project, violation))
        .toList();
  }

  private static Finding finding(final Project project, final RuffViolation violation) {
    // A syntax error has no rule code, so it gets the catch-all rule named after the tool.
    final String rule =
        violation.code == null || violation.code.isBlank() ? GATE : violation.code.strip();
    return new Finding(
        GATE,
        GATE,
        rule,
        // Ruff reports no severities, so every violation fails the gate.
        FindingSeverity.ERROR,
        violation.message == null || violation.message.isBlank()
            ? "Ruff violation."
            : violation.message.strip(),
        ReportFiles.relativize(project, violation.filename),
        violation.location == null ? null : positive(violation.location.row),
        violation.location == null ? null : positive(violation.location.column),
        null);
  }

  private static Integer positive(final Integer value) {
    return value == null || value < 1 ? null : value;
  }

  @JsonType
  static final class RuffOutput {
    List<RuffViolation> violations;

    List<RuffViolation> violationsOrEmpty() {
      return violations == null ? List.of() : violations.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class RuffViolation {
    String code;
    String message;
    String filename;
    RuffLocation location;
  }

  @JsonType
  static final class RuffLocation {
    Integer row;
    Integer column;
  }
}
