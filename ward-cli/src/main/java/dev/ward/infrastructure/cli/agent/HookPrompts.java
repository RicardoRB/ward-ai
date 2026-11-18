package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.config.WardException;
import dev.ward.domain.secrets.PromptFinding;
import dev.ward.domain.secrets.PromptInput;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Decides whether a submitted prompt goes through. The reason a prompt is blocked names rules and
 * places and never repeats the secret; the log holds the rule ids and the decision, nothing of the
 * prompt.
 */
final class HookPrompts {
  private static final Logger LOGGER = Logger.getLogger(HookPrompts.class.getName());
  private static final int MAX_LISTED = 10;
  private static final String ADVICE =
      "Remove the secret from the prompt, and rotate it if it was ever shared.";

  private HookPrompts() {}

  /**
   * Returns the reason to block {@code prompt}, or empty to let it through. A scan that cannot run,
   * for a known reason or an unexpected one, blocks the prompt with a reason that names no prompt
   * content: a prompt is never sent unscanned.
   */
  static Optional<String> blockReason(
      final HookChecks checks,
      final Path directory,
      final PromptInput prompt
  ) {
    final FutureTask<List<PromptFinding>> scan =
        new FutureTask<>(() -> checks.prompts().scan(directory, prompt));
    scan.run();
    try {
      final List<PromptFinding> found = scan.get();
      if (found.isEmpty()) {
        LOGGER.log(Level.INFO, "event=prompt-hook decision=allow");
        return Optional.empty();
      }
      LOGGER.log(
          Level.INFO,
          () ->
              "event=prompt-hook decision=block rules="
                  + found.stream().map(PromptFinding::ruleId).distinct().collect(
                      Collectors.joining(",")
                  )
      );
      return Optional.of(message(found));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Optional.of(failure(exception));
    } catch (ExecutionException exception) {
      return Optional.of(failure(exception.getCause()));
    }
  }

  private static String failure(final Throwable cause) {
    LOGGER.log(Level.INFO, "event=prompt-hook decision=block reason=scan-failed");
    final String why =
        cause instanceof WardException
            ? NativeHookMessages.safeMessage((WardException)cause)
            : "unexpected error";
    return "Ward could not scan this prompt, so it was not sent: " + why;
  }

  private static String message(final List<PromptFinding> found) {
    final StringBuilder text =
        new StringBuilder("Ward blocked this prompt: it contains a secret.");
    found.stream().limit(MAX_LISTED).forEach(finding -> text.append("\n- ").append(place(finding)));
    if (found.size() > MAX_LISTED) {
      text.append("\n- and ").append(found.size() - MAX_LISTED).append(" more");
    }
    return text.append('\n').append(ADVICE).toString();
  }

  private static String place(final PromptFinding finding) {
    return finding.ruleId()
        + (finding.inMessage() ? " in the message" : " in " + finding.file())
        + ", line "
        + finding.line()
        + ", column "
        + finding.column();
  }
}
