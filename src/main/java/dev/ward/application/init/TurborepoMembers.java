package dev.ward.application.init;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.init.ScriptReplacement;
import dev.ward.domain.init.TurboWorkspace;
import dev.ward.domain.init.TurboWorkspaceChange;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Writes a Turborepo root's members and its turbo.json, and undoes them. Each member gets the
 * build-file and config writers with the member as root, then the workspace writer runs. The root
 * installs after this, so its single install sees every member's dependencies.
 */
final class TurborepoMembers {
  private final InitProjectFiles files;
  private final InitInputs inputs;
  private final List<BuildFileChange> builds = new ArrayList<>();
  private final List<RuleFileChange> rules = new ArrayList<>();
  private TurboWorkspaceChange workspace;

  TurborepoMembers(final InitProjectFiles files, final InitInputs inputs) {
    this.files = files;
    this.inputs = inputs;
  }

  /** Returns the member plans of {@code root}, or none when it is not a Turborepo root. */
  static List<TurborepoPlan.MemberPlan> plan(
      final Project root, final QualityPreset preset, final InitInputs inputs) {
    return root.taskRunner() == TaskRunner.TURBO
        ? TurborepoPlan.memberScripts(root, preset, inputs)
        : List.of();
  }

  /**
   * Returns the workspace init writes: each member's scripts, and the turbo tasks that the root's
   * gates run, in gate order.
   */
  static TurboWorkspace workspace(
      final Path root,
      final List<TurborepoPlan.MemberPlan> members,
      final List<InitGateOption> gates) {
    final List<String> tasks =
        gates.stream()
            .map(gate -> TurborepoPlan.taskOf(gate.id()))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    return new TurboWorkspace(
        root,
        members.stream()
            .map(
                member ->
                    new TurboWorkspace.Member(
                        member.name(), member.project().root(), member.scripts()))
            .toList(),
        tasks);
  }

  /**
   * Returns the scripts of {@code members} that already run a different command than init would
   * write. Init replaces them only when the user approves.
   */
  static List<ScriptReplacement> replacements(final List<TurborepoPlan.MemberPlan> members) {
    return members.stream()
        .flatMap(
            member ->
                member.scripts().entrySet().stream()
                    .filter(
                        script ->
                            member.current().containsKey(script.getKey())
                                && !member.current().get(script.getKey()).equals(script.getValue()))
                    .map(
                        script ->
                            new ScriptReplacement(
                                member.name(),
                                script.getKey(),
                                member.current().get(script.getKey()),
                                script.getValue())))
        .toList();
  }

  /** Returns the scripts the last {@link #apply} kept, for the summary. */
  List<String> kept() {
    return workspace == null ? List.of() : workspace.kept();
  }

  /**
   * Writes the members and turbo.json of {@code root}; does nothing for any other project. An
   * existing script that differs is replaced only when {@code replaceScripts} is set.
   */
  void apply(
      final Project root,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean replaceScripts,
      final Predicate<? super CompatibilityFinding> approval) {
    if (root.taskRunner() != TaskRunner.TURBO) {
      return;
    }
    final List<TurborepoPlan.MemberPlan> members = plan(root, preset, inputs);
    for (final TurborepoPlan.MemberPlan member : members) {
      builds.add(files.buildTools().apply(member.project(), member.gates(), preset, approval));
      rules.add(files.ruleFiles().apply(member.project(), preset, member.gates()));
    }
    workspace =
        files.buildTools().applyWorkspace(workspace(root.root(), members, gates), replaceScripts);
  }

  /** Undoes what {@link #apply} changed, last changed first. */
  void rollback() {
    if (workspace != null) {
      files.buildTools().rollbackWorkspace(workspace);
    }
    rules.reversed().forEach(files.ruleFiles()::rollback);
    builds.reversed().forEach(files.buildTools()::rollback);
  }
}
