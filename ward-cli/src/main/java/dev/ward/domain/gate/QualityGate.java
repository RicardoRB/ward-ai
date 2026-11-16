package dev.ward.domain.gate;

import dev.ward.analysis.domain.port.WorkLimit;
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

  /**
   * Runs the gate like {@link #execute(Project, GateProgress)}. The calling thread holds one permit
   * of {@code limit}; a gate that works on several threads takes its other threads from it, so the
   * gates of a run and their threads stay under one limit. The default works on the calling thread
   * only.
   */
  default GateResult execute(
      final Project project,
      final GateProgress progress,
      final WorkLimit limit
  ) {
    return execute(project, progress);
  }

  /**
   * Returns whether the gate only reads the project's sources and writes only under {@code .ward},
   * so that it may run at the same time as any other gate. The default is {@code false}: the gate
   * runs alone among such gates, in configuration order.
   */
  default boolean independent() {
    return false;
  }
}
