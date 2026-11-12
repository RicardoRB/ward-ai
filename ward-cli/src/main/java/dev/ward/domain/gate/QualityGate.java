package dev.ward.domain.gate;

import dev.ward.domain.project.Project;

/**
 * A configured check that runs against a project; {@code QualityGateFactory} creates one per gate
 * ID.
 */
public interface QualityGate {

  /** Returns the gate ID from {@code ward.toml}. */
  String name();

  /** Runs the gate against {@code project} and returns its result. */
  GateResult execute(Project project);

  /**
   * Runs the gate like {@link #execute(Project)} and tells {@code progress} what it is doing. The
   * default reports nothing.
   */
  default GateResult execute(final Project project, final GateProgress progress) {
    return execute(project);
  }
}
