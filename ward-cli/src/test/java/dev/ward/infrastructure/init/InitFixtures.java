package dev.ward.infrastructure.init;

import dev.ward.application.init.InitProjectFiles;
import dev.ward.application.init.NoRuleFileGeneration;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.RuleFileGeneration;

/** Builds the file-writing ports init uses in tests, with the real adapters for every port. */
public final class InitFixtures {
  private InitFixtures() {}

  /** Returns the file-writing ports for {@code buildTools}, writing no preset rule files. */
  public static InitProjectFiles projectFiles(final BuildToolConfiguration buildTools) {
    return projectFiles(buildTools, new NoRuleFileGeneration());
  }

  /** Returns the file-writing ports for {@code buildTools} and {@code ruleFiles}. */
  public static InitProjectFiles projectFiles(
      final BuildToolConfiguration buildTools, final RuleFileGeneration ruleFiles) {
    return new InitProjectFiles(
        buildTools,
        ruleFiles,
        new ArchitectureTestGenerator(),
        new FileConfigurationStorage(),
        new OwnedSectionWriter());
  }
}
