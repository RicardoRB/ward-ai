package dev.ward.infrastructure.cli.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureEngine;
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

  @Option(
      names = "--architecture",
      description =
          "Architecture style for ArchUnit tests or the dependency-cruiser config: layered,"
              + " hexagonal, or clean.")
  private String architecture;

  @Option(
      names = "--architecture-engine",
      description =
          "Which checker enforces the architecture style: tool (the generated external tool,"
              + " default) or ward (the ward engine, with layer boundaries in ward.toml).")
  private String architectureEngine;

  /** Returns the CI provider, or {@code null} when no workflow is requested. */
  String ci() {
    return ci;
  }

  /** Returns whether the agent rules section is requested. */
  boolean agentRules() {
    return agentRules;
  }

  /** Returns the style the user named, trimmed, or {@code null} when none was given. */
  String architectureOrNull() {
    if (architecture == null || architecture.isBlank()) {
      return null;
    }
    return architecture.strip();
  }

  /**
   * Returns the engine the user named, validated, or {@code null} when none was given. An unknown
   * value is rejected here, before any file is written.
   */
  String architectureEngineOrNull() {
    if (architectureEngine == null) {
      return null;
    }
    return ArchitectureEngine.parse(architectureEngine)
        .orElseThrow(
            () ->
                new WardException(
                    "Unknown architecture engine '"
                        + architectureEngine
                        + "'. Supported engines: "
                        + ArchitectureEngine.SUPPORTED
                        + "."))
        .id();
  }

  /** Returns whether differing member scripts are replaced without asking. */
  boolean replaceScripts() {
    return replaceScripts;
  }
}
