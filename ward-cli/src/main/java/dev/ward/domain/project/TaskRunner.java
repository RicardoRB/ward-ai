package dev.ward.domain.project;

import java.util.Optional;

/**
 * The tool that runs a workspace's per-package tasks, and what init needs to know about it: the
 * name it shows, the npm package it is pinned as, and the cache it leaves in the project.
 */
public enum TaskRunner {
  /** No task runner: each gate runs from the project root. */
  NONE(null, null, null, false),

  /** Turborepo: each gate runs as {@code turbo run <task>} from the workspace root. */
  TURBO("Turborepo", "turbo", ".turbo/", true),

  /**
   * A package inside a Turborepo workspace. Its gates run as its scripts, and the workspace root
   * installs its dependencies.
   */
  TURBO_MEMBER("Turborepo", null, null, false);

  private final String displayName;
  private final String packageName;
  private final String cacheDirectory;
  private final boolean rootRunner;

  TaskRunner(
      final String displayName,
      final String packageName,
      final String cacheDirectory,
      final boolean rootRunner) {
    this.displayName = displayName;
    this.packageName = packageName;
    this.cacheDirectory = cacheDirectory;
    this.rootRunner = rootRunner;
  }

  /** Returns the name this runner shows in output, or empty for no runner. */
  public Optional<String> displayName() {
    return Optional.ofNullable(displayName);
  }

  /** Returns the npm package init pins in the root's dev dependencies, when it has one. */
  public Optional<String> packageName() {
    return Optional.ofNullable(packageName);
  }

  /**
   * Returns the Git ignore line for the cache directory the runner leaves in the root, which Git
   * and Prettier then skip; empty when the runner leaves none.
   */
  public String gitignoreEntry() {
    return cacheDirectory == null ? "" : cacheDirectory + "\n";
  }

  /**
   * Returns whether this runner's workspace root runs the package tasks, so its gates are task
   * gates and it has its own config file.
   */
  public boolean isRootRunner() {
    return rootRunner;
  }

  /** Returns whether this runner runs package tasks, from the root or from a member. */
  public boolean runsPackageTasks() {
    return this != NONE;
  }
}
