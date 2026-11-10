package dev.ward.application.gate;

import java.nio.file.Path;

/** Chooses and configures the rules of a {@code ward} run, once the project root is known. */
@FunctionalInterface
public interface RuleSetup {

  /**
   * Returns the rules to run in the project at {@code root}, or the problems that stop the run. The
   * detected Java release is the default of a rule's {@code java-release} option.
   */
  RuleSet resolve(Path root, int javaRelease);
}
