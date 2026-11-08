package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.util.List;

/** A real Java tool that is run over the fixtures of every rule it has a counterpart for. */
interface ParityTool {

  /** The catalog key of the tool's counterparts. */
  String name();

  /** Whether the tool reports columns; when it does not, only lines are compared. */
  boolean reportsColumns();

  /** Runs the tool over the fixtures of {@code targets} and returns what it reported. */
  ParityRun run(ParityEnvironment environment, List<ParityTarget> targets)
      throws IOException, InterruptedException;
}
