package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.learning.LearningOutcome;
import dev.ward.domain.learning.LearningPrompt;
import java.util.ArrayList;
import java.util.List;

/** Plain-text feedback the Claude Code hook gives the agent. */
final class ClaudeHookFeedback {
  private ClaudeHookFeedback() {}

  /** Failure lines for stderr: quality failures, then learning prompts. */
  static List<String> failures(final CheckReport report, final LearningOutcome learning) {
    final List<String> lines = new ArrayList<>();
    if (!report.passed()) {
      lines.add(qualityFailures(report));
    }
    learning.prompts().forEach(prompt -> lines.add(prompt.instruction()));
    return lines;
  }

  /** Turn-end findings: the quality failures. */
  static String turnEnd(final CheckReport report) {
    return qualityFailures(report);
  }

  static String learningContext(final LearningOutcome learning) {
    return learning.prompts().stream()
        .map(LearningPrompt::instruction)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  private static String qualityFailures(final CheckReport report) {
    return report.results().stream()
        .filter(result -> !result.passed() && result.status() != GateStatus.SKIPPED)
        .map(
            result ->
                result.name()
                    + ": "
                    + (result.summary() == null ? result.status() : result.summary()))
        .reduce((a, b) -> a + System.lineSeparator() + b)
        .orElse("Ward quality check failed");
  }
}
