package dev.ward.application.init;

import java.nio.file.Path;
import java.util.Optional;

/** Turns on prompt scanning in the configuration of the project at a directory. */
@FunctionalInterface
public interface PromptTableEnabler {
  /** An enabler that changes nothing. */
  PromptTableEnabler NONE = directory -> Optional.empty();

  /** Returns what it did or why it did not, to print; empty when there is no project. */
  Optional<String> enable(Path directory);
}
