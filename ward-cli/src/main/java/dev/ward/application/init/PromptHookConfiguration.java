package dev.ward.application.init;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.init.ConfigurationStorage;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.OwnedSectionGeneration;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Turns on prompt scanning in the {@code ward.toml} of an existing project, for {@code ward
 * integrate --prompt-hook}: a marked, Ward-owned block with {@code [checks.secrets.prompts]}. A
 * table the user wrote is never changed, and a project without a {@code secrets} check is told how.
 */
public final class PromptHookConfiguration implements PromptTableEnabler {
  static final String BEGIN = "# ward-owned: prompt-hook v1 begin";
  static final String END = "# ward-owned: prompt-hook v1 end";
  private static final Pattern SECRETS = Pattern.compile("(?m)^\\[checks\\.secrets]\\s*$");
  private static final Pattern PROMPTS = Pattern.compile("(?m)^\\[checks\\.secrets\\.prompts]");

  private final ProjectDetector detector;
  private final ConfigurationStorage storage;
  private final OwnedSectionGeneration sections;

  /** Creates the enabler over the project detection and the file ports. */
  @Inject
  public PromptHookConfiguration(
      final ProjectDetector detector,
      final ConfigurationStorage storage,
      final OwnedSectionGeneration sections
  ) {
    this.detector = detector;
    this.storage = storage;
    this.sections = sections;
  }

  @Override
  public Optional<String> enable(final Path directory) {
    final Optional<Project> project = detector.detect(directory);
    if (project.isEmpty()) {
      return Optional.empty();
    }
    final Path file = project.get().root().resolve("ward.toml");
    final Optional<String> toml = storage.read(file);
    if (toml.isEmpty()) {
      return Optional.of(
          "No ward.toml: run ward init, then add [checks.secrets.prompts] to scan prompts."
      );
    }
    final String text = toml.get();
    if (!text.contains(BEGIN) && PROMPTS.matcher(text).find()) {
      return Optional.of("ward.toml already has a [checks.secrets.prompts] table; left unchanged.");
    }
    if (!SECRETS.matcher(text).find()) {
      return Optional.of(
          "ward.toml has no [checks.secrets] check; add one and [checks.secrets.prompts] with"
              + " enabled = true to scan prompts."
      );
    }
    sections.apply(
        new OwnedSection(file, BEGIN, END, "[checks.secrets.prompts]\nenabled = true\n", false)
    );
    return Optional.of("Enabled [checks.secrets.prompts] in ward.toml.");
  }
}
