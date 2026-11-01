package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/** Reads SwiftLint violations from the {@code --reporter json} array printed on standard output. */
public final class SwiftLintJsonFindings implements GateFindingSource {
  static final String GATE = "swiftlint";
  private static final Logger LOGGER = Logger.getLogger(SwiftLintJsonFindings.class.getName());
  // `swift run` may print build lines such as "[1/2] Compiling" first, so the array starts at a
  // line that is "[" alone or opens an object or closes at once.
  private static final Pattern ARRAY_START =
      Pattern.compile("^\\[\\s*(?:\\{|\\]|$)", Pattern.MULTILINE);
  private static final String WARNING = "warning";

  private final JsonCodec codec;

  /** Creates the adapter that decodes SwiftLint output with {@code codec}. */
  @Inject
  public SwiftLintJsonFindings(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final String stdout = Objects.toString(result.stdout(), "");
    final Matcher start = ARRAY_START.matcher(stdout);
    if (!start.find()) {
      return List.of();
    }
    final SwiftLintOutput output;
    try {
      output =
          codec.fromJson(
              "{\"violations\":" + stdout.substring(start.start()) + "}", SwiftLintOutput.class);
    } catch (JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(Level.INFO, () -> "event=findings-parse-failed gate=" + GATE);
      return List.of();
    }
    return output.violationsOrEmpty().stream()
        .map(violation -> finding(project, violation))
        .toList();
  }

  private static Finding finding(final Project project, final SwiftLintViolation violation) {
    return new Finding(
        GATE,
        GATE,
        violation.ruleId == null || violation.ruleId.isBlank() ? GATE : violation.ruleId.strip(),
        WARNING.equals(Objects.toString(violation.severity, "").toLowerCase(Locale.ROOT))
            ? FindingSeverity.WARNING
            : FindingSeverity.ERROR,
        violation.reason == null || violation.reason.isBlank()
            ? "SwiftLint violation."
            : violation.reason.strip(),
        ReportFiles.relativize(project, violation.file),
        positive(violation.line),
        positive(violation.character),
        null);
  }

  private static Integer positive(final Integer value) {
    return value == null || value < 1 ? null : value;
  }

  @JsonType
  static final class SwiftLintOutput {
    List<SwiftLintViolation> violations;

    List<SwiftLintViolation> violationsOrEmpty() {
      return violations == null ? List.of() : violations.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class SwiftLintViolation {
    @JsonProperty("rule_id")
    String ruleId;

    String reason;
    String severity;
    String file;
    Integer line;
    Integer character;
  }
}
