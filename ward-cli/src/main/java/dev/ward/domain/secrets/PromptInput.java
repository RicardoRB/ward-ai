package dev.ward.domain.secrets;

import java.util.List;
import java.util.Objects;

/**
 * What an agent prompt carries: the typed message, which has no path, and the files it attaches or
 * references, by the path the agent gave.
 */
public record PromptInput(String message, List<String> files) {
  /** Copies the files. */
  public PromptInput {
    Objects.requireNonNull(message, "message");
    files = List.copyOf(files);
  }

  /** A prompt with a message and no attached file. */
  public PromptInput(final String message) {
    this(message, List.of());
  }
}
