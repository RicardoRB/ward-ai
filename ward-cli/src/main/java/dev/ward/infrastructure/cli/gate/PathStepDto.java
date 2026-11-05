package dev.ward.infrastructure.cli.gate;

import dev.ward.domain.gate.FindingStep;
import java.util.List;
import org.apache.fory.json.annotation.JsonType;

/**
 * One step of a finding's path, in the report schema: its file, line, column and message.
 *
 * @param file project-root-relative path of the step
 * @param line 1-based line
 * @param column 1-based column
 * @param message what happens at the step
 */
@JsonType
public record PathStepDto(String file, int line, int column, String message) {

  /**
   * Converts the steps of a path, in order.
   *
   * @param steps the steps of one finding
   * @return one DTO per step
   */
  static List<PathStepDto> allOf(final List<FindingStep> steps) {
    return steps.stream()
        .map(step -> new PathStepDto(step.file(), step.line(), step.column(), step.message()))
        .toList();
  }
}
