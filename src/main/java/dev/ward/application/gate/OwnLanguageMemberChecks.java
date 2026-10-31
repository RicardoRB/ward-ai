package dev.ward.application.gate;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The members of a Turborepo root that hold another language's build file. Each one is checked as
 * its own project, and its results name it by its path below the repository root.
 */
final class OwnLanguageMemberChecks {
  private OwnLanguageMemberChecks() {}

  /**
   * Returns the directories of the own-language members of {@code project}, none unless it is a
   * Turborepo root.
   */
  static List<Path> directories(final Project project, final WorkspaceMembers members) {
    return project.taskRunner().isRootRunner()
        ? members.ownLanguageMembers(project.root()).stream()
            .map(WorkspaceMember::directory)
            .toList()
        : List.of();
  }

  /**
   * Runs {@code check} on each member directory after the root's {@code own} report, stopping at
   * the first failure when {@code failFast}, and merges the results.
   */
  static CheckReport checkAll(
      final CheckReport own,
      final List<Path> directories,
      final boolean failFast,
      final Function<Path, CheckReport> check) {
    final List<CheckReport> nested = new ArrayList<>();
    for (final Path directory : directories) {
      if (failFast && !merge(own, nested).passed()) {
        break;
      }
      nested.add(check.apply(directory));
    }
    return merge(own, nested);
  }

  /**
   * Runs {@code check} on each member directory that holds one of the edited {@code paths}, or on
   * every member when none is edited, and merges the results into {@code own}.
   */
  static CheckReport partial(
      final CheckReport own,
      final List<Path> directories,
      final List<Path> paths,
      final BiFunction<Path, List<Path>, CheckReport> check) {
    final List<CheckReport> nested = new ArrayList<>();
    for (final Path directory : directories) {
      final List<Path> memberPaths =
          paths.stream().filter(path -> inside(path, directory)).toList();
      if (paths.isEmpty() || !memberPaths.isEmpty()) {
        nested.add(check.apply(directory, memberPaths));
      }
    }
    return merge(own, nested);
  }

  /** Returns whether {@code path} lies in one of {@code directories}. */
  static boolean insideAny(final Path path, final List<Path> directories) {
    return directories.stream().anyMatch(directory -> inside(path, directory));
  }

  /**
   * Returns whether {@code path} lies in {@code directory}, comparing normalized absolute paths.
   */
  static boolean inside(final Path path, final Path directory) {
    return path.toAbsolutePath().normalize().startsWith(directory);
  }

  /** Returns {@code own} with each nested report's results appended, named by its project path. */
  static CheckReport merge(final CheckReport own, final List<CheckReport> nested) {
    final Path root = own.project().root();
    final List<GateResult> results = new ArrayList<>(own.results());
    nested.forEach(
        report -> {
          final String label = label(root, report.project().root());
          report.results().forEach(result -> results.add(named(result, label)));
        });
    return own.withResults(results);
  }

  private static GateResult named(final GateResult result, final String label) {
    return result
        .withName(label + ": " + result.name())
        .withFindings(rebased(result.findings(), label));
  }

  private static GateFindings rebased(final GateFindings findings, final String label) {
    return new GateFindings(
        findings.items().stream().map(finding -> rebased(finding, label)).toList(),
        findings.total());
  }

  private static Finding rebased(final Finding finding, final String label) {
    if (finding.file() == null || Path.of(finding.file()).isAbsolute()) {
      return finding;
    }
    return new Finding(
        finding.gateId(),
        finding.tool(),
        finding.ruleId(),
        finding.severity(),
        finding.message(),
        label + "/" + finding.file(),
        finding.line(),
        finding.column(),
        finding.helpUri());
  }

  private static String label(final Path root, final Path member) {
    return root.relativize(member).toString().replace('\\', '/');
  }
}
