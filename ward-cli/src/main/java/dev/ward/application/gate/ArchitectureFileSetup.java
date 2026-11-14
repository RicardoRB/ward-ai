package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds the general rules of {@code ward-architecture.toml} to the rules another setup chose. A file
 * with problems stops the run: its problems join those of the inner setup and no rule runs.
 */
final class ArchitectureFileSetup implements RuleSetup {
  private static final Logger LOGGER = Logger.getLogger(ArchitectureFileSetup.class.getName());

  private final RuleSetup inner;
  private final String file;
  private final AnalysisRuleSource source;

  /**
   * Creates the setup.
   *
   * @param inner chooses the other rules
   * @param file the {@code architecture-file} path, or {@code null} for the default name
   * @param source reads the file
   */
  ArchitectureFileSetup(final RuleSetup inner, final String file, final AnalysisRuleSource source) {
    this.inner = inner;
    this.file = file;
    this.source = source;
  }

  @Override
  public RuleSet resolve(final Path root, final int javaRelease) {
    final RuleSet chosen = inner.resolve(root, javaRelease);
    final Optional<ArchitectureFile> architecture = source.architecture(root, file);
    if (architecture.isEmpty()) {
      return chosen;
    }
    final List<String> problems = new ArrayList<>(chosen.problems());
    problems.addAll(architecture.get().problems());
    final List<RuleDefinition> rules = new ArrayList<>(chosen.rules());
    rules.addAll(ArchitectureGraphRules.general(architecture.get().rules()));
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine(
          "event=architecture-rules file="
              + architecture.get().name()
              + " rules="
              + architecture.get().rules().size());
    }
    return new RuleSet(rules, chosen.excludePaths(), problems);
  }
}
