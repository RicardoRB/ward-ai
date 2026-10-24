package dev.ward.domain.gate;

import dev.ward.domain.project.Project;
import java.time.Instant;

/** Reads the mutant statuses that a mutation testing tool wrote for a project. */
@FunctionalInterface
public interface MutationReport {

  /** Returns the counts of the report written at or after {@code since}, or why it is unusable. */
  MutationCounts read(Project project, Instant since);
}
