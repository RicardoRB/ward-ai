package dev.ward.infrastructure.cli.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureEngine;
import dev.ward.domain.init.ArchitectureGates;
import dev.ward.domain.project.Project;
import java.util.Collections;
import java.util.List;

/**
 * Resolves which checker enforces the architecture style: the option when given, otherwise the
 * wizard's question when the engine resolves every language, otherwise the external tool.
 */
final class EngineQuestions {
  private final String requested;
  private final ArchitectureEngineQuestion question;
  private final QuestionProgress progress;
  private final boolean useDefaults;
  private final Project project;

  EngineQuestions(
      final String requested,
      final ArchitectureEngineQuestion question,
      final QuestionProgress progress,
      final boolean useDefaults,
      final Project project) {
    this.requested = requested;
    this.question = question;
    this.progress = progress;
    this.useDefaults = useDefaults;
    this.project = project;
  }

  /**
   * Returns the checker of the style {@code architecture} for {@code gates} under {@code preset}.
   */
  ArchitectureEngine resolve(final String architecture, final List<String> gates) {
    final List<String> chosen = gates == null ? List.of() : gates;
    if (requested != null) {
      final ArchitectureEngine engine = parse(requested);
      if (engine == ArchitectureEngine.WARD) {
        requireEngineSupport();
      }
      return engine;
    }
    if (useDefaults
        || architecture == null
        || Collections.disjoint(chosen, ArchitectureGates.IDS)
        || ArchitectureEngine.unsupportedLanguage(project.languages()).isPresent()) {
      return ArchitectureEngine.TOOL;
    }
    progress.expect(1);
    progress.step();
    return question.select();
  }

  private static ArchitectureEngine parse(final String id) {
    return ArchitectureEngine.parse(id)
        .orElseThrow(
            () ->
                new WardException(
                    "Unknown architecture engine '"
                        + id
                        + "'. Supported engines: "
                        + ArchitectureEngine.SUPPORTED
                        + "."));
  }

  /** Refuses {@code ward} for a project whose language the engine cannot resolve. */
  private void requireEngineSupport() {
    ArchitectureEngine.unsupportedLanguage(project.languages())
        .ifPresent(
            language -> {
              throw new WardException(
                  "The ward engine cannot check "
                      + language.displayName()
                      + " yet: it resolves no dependencies for that language. Use"
                      + " --architecture-engine tool.");
            });
  }
}
