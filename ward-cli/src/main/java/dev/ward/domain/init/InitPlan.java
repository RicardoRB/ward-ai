package dev.ward.domain.init;

import dev.ward.domain.agent.PlannedFileChange;
import java.util.List;

/** Everything initialization would change, computed without touching the project. */
public record InitPlan(
    List<PlannedFileChange> files,
    List<PlannedGateInstallation> gateInstallations,
    List<ScriptReplacement> replacements) {
  /** Creates a plan that installs no gate executables and replaces no scripts. */
  public InitPlan(final List<PlannedFileChange> files) {
    this(files, List.of(), List.of());
  }

  /** Creates a plan that replaces no scripts. */
  public InitPlan(
      final List<PlannedFileChange> files, final List<PlannedGateInstallation> gateInstallations) {
    this(files, gateInstallations, List.of());
  }

  /** Copies the lists; missing values mean nothing to install or replace. */
  public InitPlan {
    files = List.copyOf(files);
    gateInstallations = gateInstallations == null ? List.of() : List.copyOf(gateInstallations);
    replacements = replacements == null ? List.of() : List.copyOf(replacements);
  }

  /** Whether a file in the plan would stop initialization. */
  public boolean hasConflict() {
    return files.stream().anyMatch(file -> file.action() == PlannedFileChange.Action.CONFLICT);
  }

  /** The entries worth showing to a user: every file except those left unchanged. */
  public List<PlannedFileChange> visibleFiles() {
    return files.stream()
        .filter(file -> file.action() != PlannedFileChange.Action.UNCHANGED)
        .toList();
  }
}
