package dev.ward.application.init;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.ConfigurationStorage;
import dev.ward.domain.init.OwnedSectionGeneration;
import dev.ward.domain.init.RuleFileGeneration;
import java.util.function.Supplier;

/**
 * The outbound ports initialization uses to write project files: the build file, rule files,
 * Ward-owned sections such as the CI workflow, and {@code ward.toml}. They are planned, applied,
 * and rolled back together.
 *
 * @param buildTools writes quality plugins into the build file
 * @param ruleFiles writes the preset's rule files
 * @param architectureTables answers the source roots and modules of the ward architecture tables
 * @param storage supplies the port that reads and writes {@code ward.toml}
 * @param sections writes the CI workflow and the agent rules section
 */
public record InitProjectFiles(
    BuildToolConfiguration buildTools,
    RuleFileGeneration ruleFiles,
    ArchitectureTables architectureTables,
    Supplier<ConfigurationStorage> storage,
    OwnedSectionGeneration sections) {

  /** Groups the file-writing ports. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTables architectureTables,
      final ConfigurationStorage configuration,
      final OwnedSectionGeneration sections) {
    this(buildTools, ruleFiles, architectureTables, () -> configuration, sections);
  }

  /** Groups the file-writing ports for a setup that writes no owned sections. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTables architectureTables,
      final ConfigurationStorage configuration) {
    this(buildTools, ruleFiles, architectureTables, configuration, new NoOwnedSectionGeneration());
  }

  /** Groups the file-writing ports for a setup that writes no rule files. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final ArchitectureTables architectureTables,
      final ConfigurationStorage configuration) {
    this(buildTools, new NoRuleFileGeneration(), architectureTables, configuration);
  }

  /** Returns the port that reads and writes {@code ward.toml}. */
  public ConfigurationStorage configuration() {
    return storage.get();
  }
}
