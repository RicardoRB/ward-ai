package dev.ward.application.init.node.turbo;

import dev.ward.domain.project.BuildTool;
import java.util.List;

/**
 * How a workspace root runs its package tasks: the command for a task across every package, and the
 * partial-check item that narrows that command to the package an edit is in. A task runner adds one
 * implementation and one registry entry.
 */
public interface TaskRunnerStrategy {
  /** Returns the command that runs {@code task} for every package, through {@code manager}. */
  List<String> rootTaskCommand(BuildTool manager, String task);

  /** Returns the item that narrows a root task to the package holding an edit. */
  String packageFilter();
}
