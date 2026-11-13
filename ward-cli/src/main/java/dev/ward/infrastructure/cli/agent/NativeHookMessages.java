package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.gate.CheckReport;

/** Bounded, agent-facing messages written by the native hook. */
final class NativeHookMessages {
  private static final int MAX_FEEDBACK_CHARS = 8_192;
  private static final int MAX_ITEMS = 20;

  private NativeHookMessages() {}

  static String failureText(final CheckReport report) {
    return bounded(
        report.passed()
            ? "Ward validation failed."
            : report.results().stream()
                .map(Object::toString)
                .limit(MAX_ITEMS)
                .reduce("Ward quality findings:", (a, b) -> a + "\n" + b));
  }

  static String safeMessage(final Exception exception) {
    final String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : bounded(message);
  }

  static String bounded(final String value) {
    return value.length() <= MAX_FEEDBACK_CHARS
        ? value
        : value.substring(0, MAX_FEEDBACK_CHARS) + "…";
  }
}
