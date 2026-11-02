package dev.ward.infrastructure.cli.init;

import picocli.CommandLine.Option;

/**
 * Options that shape the Ward-owned files init writes beyond the gate configuration, and the Xcode
 * scheme that the Swift test gate runs.
 */
public class GeneratedFileOptions {
  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Option(
      names = "--ci",
      description = "Write a Ward-owned CI workflow that runs the ci profile: github.")
  private String ci;

  @Option(
      names = "--agent-rules",
      description = "Write the selected gates' rules into a Ward-owned section of AGENTS.md.")
  private boolean agentRules;

  @Option(
      names = "--replace-scripts",
      description =
          "Replace the Turborepo member scripts that differ from the plan, without asking.")
  private boolean replaceScripts;

  /** Returns the CI provider, or {@code null} when no workflow is requested. */
  String ci() {
    return ci;
  }

  /** Returns whether the agent rules section is requested. */
  boolean agentRules() {
    return agentRules;
  }

  /** Returns whether differing member scripts are replaced without asking. */
  boolean replaceScripts() {
    return replaceScripts;
  }
}
