package dev.ward.application.gate;

import dev.ward.domain.secrets.PromptFinding;
import dev.ward.domain.secrets.PromptInput;
import java.nio.file.Path;
import java.util.List;

/** Scans an agent prompt for the project at a directory. */
@FunctionalInterface
public interface PromptScanner {
  /** A scanner that finds nothing, for hooks that do not scan prompts. */
  PromptScanner NONE = (directory, prompt) -> List.of();

  /**
   * Returns where the prompt holds a secret; empty when it holds none or no check scans prompts.
   *
   * @throws dev.ward.domain.config.WardException if the prompt cannot be scanned completely
   */
  List<PromptFinding> scan(Path directory, PromptInput prompt);
}
