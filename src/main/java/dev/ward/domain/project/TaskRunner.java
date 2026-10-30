package dev.ward.domain.project;

/** The tool that runs a workspace's per-package tasks. */
public enum TaskRunner {
  /** No task runner: each gate runs from the project root. */
  NONE,

  /** Turborepo: each gate runs as {@code turbo run <task>} from the workspace root. */
  TURBO,

  /**
   * A package inside a Turborepo workspace. Its gates run as its scripts, and the workspace root
   * installs its dependencies.
   */
  TURBO_MEMBER
}
