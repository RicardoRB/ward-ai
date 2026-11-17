package dev.ward.application.gate;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.project.Project;
import dev.ward.domain.secrets.PromptFinding;
import dev.ward.domain.secrets.PromptInput;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Scans agent prompts for the checks whose {@code [checks.<name>.prompts]} table is enabled. A
 * project without Ward configuration, or with no enabled table, scans nothing.
 */
public final class PromptGuard implements PromptScanner {
  private static final Logger LOGGER = Logger.getLogger(PromptGuard.class.getName());

  private final ProjectDetector detector;
  private final WardConfigurationReader reader;
  private final SecretGates secrets;

  /** Creates the guard. */
  @Inject
  public PromptGuard(
      final ProjectDetector detector,
      final WardConfigurationReader reader,
      final SecretGates secrets
  ) {
    this.detector = detector;
    this.reader = reader;
    this.secrets = secrets;
  }

  /**
   * Returns the findings in {@code prompt} for the project at {@code directory}; empty when no
   * check scans prompts.
   *
   * @throws dev.ward.domain.config.WardException if the configuration or the scan fails
   */
  @Override
  public List<PromptFinding> scan(final Path directory, final PromptInput prompt) {
    final Optional<Project> project = detector.detect(directory);
    if (project.isEmpty()) {
      return List.of();
    }
    final Path root = project.get().root();
    final List<GateConfiguration> checks =
        reader.read(root.resolve(WardConfiguration.FILE_NAME)).enabledGates().values().stream()
            .filter(check -> SupportedQualityGates.SECRETS.equals(check.type()))
            .filter(check -> check.secrets().scanPrompts())
            .toList();
    LOGGER.log(Level.INFO, () -> "event=prompt-guard checks=" + checks.size());
    return checks.stream()
        .flatMap(check -> secrets.promptScan(check).scan(root, prompt).stream())
        .toList();
  }
}
