package dev.ward.application.gate;

import java.nio.file.Path;
import java.util.Optional;

/** Port that loads the declarative analysis rules the {@code ward} gate can select. */
@FunctionalInterface
public interface AnalysisRuleSource {

  /** Loads every rule file. A file that cannot be used is reported and contributes no rules. */
  AnalysisRuleLoad load();

  /**
   * Reads the rule configuration file of the project at {@code root}. {@code file} is the
   * project-relative path set by {@code rules-file}, or {@code null} for {@code ward-rules.toml}.
   * Returns empty when the default file does not exist, and a file that reports a problem when a
   * configured one cannot be used. The default has no configuration file.
   */
  default Optional<RuleConfigFile> configuration(final Path root, final String file) {
    return Optional.empty();
  }
}
