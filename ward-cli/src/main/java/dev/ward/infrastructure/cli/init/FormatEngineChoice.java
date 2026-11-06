package dev.ward.infrastructure.cli.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.FormatEngine;
import dev.ward.domain.init.FormatGates;
import java.util.List;

/**
 * Resolves which formatter checks the sources: the option when given, otherwise the wizard's
 * question when a selected gate formats them, otherwise the external tools.
 */
final class FormatEngineChoice {
  private final String requested;
  private final FormatEngineQuestion question;
  private final QuestionProgress progress;
  private final boolean useDefaults;

  FormatEngineChoice(
      final String requested,
      final FormatEngineQuestion question,
      final QuestionProgress progress,
      final boolean useDefaults) {
    this.requested = requested;
    this.question = question;
    this.progress = progress;
    this.useDefaults = useDefaults;
  }

  /** Returns the formatter for {@code gates}. */
  FormatEngine resolve(final List<String> gates) {
    if (requested != null) {
      return FormatEngine.parse(requested)
          .orElseThrow(() -> new WardException("Unknown format engine '" + requested + "'."));
    }
    if (useDefaults || !FormatGates.formats(gates)) {
      return FormatEngine.TOOL;
    }
    progress.expect(1);
    progress.step();
    return question.select();
  }
}
