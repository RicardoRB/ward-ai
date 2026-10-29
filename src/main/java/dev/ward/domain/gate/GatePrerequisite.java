package dev.ward.domain.gate;

/**
 * Why a gate could not run in this environment, and how to fix it.
 *
 * @param kind what is missing: an executable or a runtime
 * @param executable the executable that could not start, or {@code null} for a runtime problem
 * @param required the version the project needs, or {@code null} when not a version problem
 * @param detected the version the environment has, or {@code null} when unknown
 * @param remediation the instruction that fixes the problem
 */
public record GatePrerequisite(
    Kind kind, String executable, String required, String detected, String remediation) {

  /** What is missing from the environment. */
  public enum Kind {
    /** The gate's executable could not be started. */
    EXECUTABLE,
    /** The runtime is older than the project requires. */
    RUNTIME
  }
}
