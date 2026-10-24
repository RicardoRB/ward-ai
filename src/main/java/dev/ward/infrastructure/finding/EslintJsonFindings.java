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

/** Reads ESLint problems from the {@code --format json} array printed on standard output. */
public final class EslintJsonFindings implements GateFindingSource {
  static final String GATE = "eslint";
  private static final Logger LOGGER = Logger.getLogger(EslintJsonFindings.class.getName());
  private static final int ERROR_SEVERITY = 2;
  // Package managers may print lines before the tool output, so the array starts at a line's '['.
  private static final Pattern ARRAY_START = Pattern.compile("^\\[", Pattern.MULTILINE);

  private final JsonCodec codec;

  /** Creates the adapter that decodes ESLint output with {@code codec}. */
  @Inject
  public EslintJsonFindings(final JsonCodec codec) {
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
    final EslintOutput output;
    try {
      output =
          codec.fromJson(
              "{\"results\":" + stdout.substring(start.start()) + "}", EslintOutput.class);
    } catch (JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(Level.INFO, () -> "event=findings-parse-failed gate=" + GATE);
      return List.of();
    }
    return output.resultsOrEmpty().stream()
        .flatMap(
            file -> file.messagesOrEmpty().stream().map(message -> finding(project, file, message)))
        .toList();
  }

  private static Finding finding(
      final Project project, final EslintFile file, final EslintMessage message) {
    // A fatal parsing error has no rule ID, so it gets the catch-all rule named after the tool.
    final String rule =
        message.ruleId == null || message.ruleId.isBlank() ? GATE : message.ruleId.strip();
    return new Finding(
        GATE,
        GATE,
        rule,
        message.severity != null && message.severity == ERROR_SEVERITY
            ? FindingSeverity.ERROR
            : FindingSeverity.WARNING,
        message.message == null || message.message.isBlank()
            ? "ESLint problem."
            : message.message.strip(),
        ReportFiles.relativize(project, file.filePath),
        positive(message.line),
        positive(message.column),
        null);
  }

  private static Integer positive(final Integer value) {
    return value == null || value < 1 ? null : value;
  }

  @JsonType
  static final class EslintOutput {
    List<EslintFile> results;

    List<EslintFile> resultsOrEmpty() {
      return results == null ? List.of() : results.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class EslintFile {
    String filePath;
    List<EslintMessage> messages;

    List<EslintMessage> messagesOrEmpty() {
      return messages == null ? List.of() : messages.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class EslintMessage {
    String ruleId;
    Integer severity;
    String message;
    Integer line;
    Integer column;
  }
}
