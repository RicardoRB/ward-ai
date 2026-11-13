package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.gate.CheckReport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a native hook's checks in each project the hook scope selects and collects their findings. A
 * hook started in a project checks only that project, with the findings it always reported; a
 * repository-level hook names the project before each project's findings.
 */
final class NativeHookChecks {
  private NativeHookChecks() {}

  /** Returns the findings of the partial checks for the files {@code event} edited, if any. */
  static Optional<String> afterEdit(
      final HookChecks checks, final NativeHookInput event, final Path directory) {
    final Map<Path, List<Path>> projects =
        checks.scope().editedProjects(directory, NativeHookEditedPaths.of(event, directory));
    final List<String> findings = new ArrayList<>();
    projects.forEach(
        (project, paths) ->
            collect(
                findings,
                directory,
                project,
                checks.checks().checkPartial(project, paths, Set.of())));
    return joined(findings);
  }

  /** Returns the findings of the turn-end checks, if any. */
  static Optional<String> turnEnd(final HookChecks checks, final Path directory) {
    final List<String> findings = new ArrayList<>();
    for (final Path project : checks.scope().turnEndProjects(directory)) {
      collect(findings, directory, project, checks.checks().check(project));
    }
    return joined(findings);
  }

  private static void collect(
      final List<String> findings,
      final Path directory,
      final Path project,
      final CheckReport report) {
    if (report.passed()) {
      return;
    }
    final String text = NativeHookMessages.failureText(report);
    findings.add(
        project.equals(directory)
            ? text
            : "Ward checked " + directory.relativize(project) + ":\n" + text);
  }

  private static Optional<String> joined(final List<String> findings) {
    return findings.isEmpty()
        ? Optional.empty()
        : Optional.of(NativeHookMessages.bounded(String.join("\n", findings)));
  }
}
