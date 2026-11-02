package dev.ward.domain.gate;

import dev.ward.domain.project.Project;

/** Reads the line coverage of a project's production code from a coverage tool's output. */
@FunctionalInterface
public interface CoverageReport {

  /** Returns the lines of the report that {@code stdout} prints or names, or why it is unusable. */
  LineCoverage read(Project project, String stdout);
}
