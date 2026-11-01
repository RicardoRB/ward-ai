package dev.ward.application.init.node.turbo;

import dev.ward.application.init.InitInputs;
import dev.ward.application.init.InitProjectFiles;
import dev.ward.application.init.MemberPlanning;
import dev.ward.application.init.MemberWriter;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.init.ScriptReplacement;
import dev.ward.domain.init.TurboWorkspace;
import dev.ward.domain.init.TurboWorkspaceChange;
import dev.ward.domain.project.Project;
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
public final class TurborepoMembers implements MemberWriter {
  private final InitProjectFiles files;
  private final InitInputs inputs;
  private final List<BuildFileChange> builds = new ArrayList<>();
  private final List<RuleFileChange> rules = new ArrayList<>();
  private TurboWorkspaceChange workspace;

  /** Creates the writer of {@code files} and {@code inputs}' Turborepo members. */
  public TurborepoMembers(final InitProjectFiles files, final InitInputs inputs) {
    this.files = files;
    this.inputs = inputs;
  }

  /** Returns the member plans of {@code root}, or none when it is not a Turborepo root. */
  public static List<TurborepoPlan.MemberPlan> plan(
      final Project root, final QualityPreset preset, final InitInputs inputs) {
    return root.taskRunner().isRootRunner()
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
  public static List<ScriptReplacement> replacements(final List<TurborepoPlan.MemberPlan> members) {
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

  /**
   * Lists a Turborepo root's member changes and turbo.json, in the order they are applied; any
   * other project has none.
   */
  public static List<PlannedFileChange> planChanges(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved,
      final MemberPlanning planning) {
    if (!project.taskRunner().isRootRunner()) {
      return List.of();
    }
    final List<TurborepoPlan.MemberPlan> members = plan(project, preset, planning.inputs());
    final List<PlannedFileChange> changes = new ArrayList<>();
    for (final TurborepoPlan.MemberPlan member : members) {
      changes.addAll(planning.buildFiles(member.project(), member.gates(), preset, approved));
      changes.addAll(planning.ruleFiles(member.project(), preset, member.gates()));
      changes.add(
          new PlannedFileChange(
              member.project().root().resolve("package.json"),
              PlannedFileChange.Action.MODIFY,
              member.scripts().entrySet().stream()
                  .map(script -> scriptDetail(member, script.getKey()))
                  .toList()));
    }
    changes.add(
        new PlannedFileChange(
            project.root().resolve("turbo.json"),
            PlannedFileChange.Action.MODIFY,
            workspace(project.root(), members, gates).tasks().stream()
                .map(task -> "add task " + task)
                .toList()));
    return changes;
  }

  /** Names a member script as added, or as a replacement that needs approval. */
  private static String scriptDetail(final TurborepoPlan.MemberPlan member, final String script) {
    return member.current().containsKey(script)
            && !member.current().get(script).equals(member.scripts().get(script))
        ? "replace " + script + " script (requires approval)"
        : "add script " + script;
  }

  /** Returns the scripts the last {@link #apply} kept, for the summary. */
  @Override
  public List<String> kept() {
    return workspace == null ? List.of() : workspace.kept();
  }

  /**
   * Writes the members and turbo.json of {@code root}; does nothing for any other project. An
   * existing script that differs is replaced only when {@code replaceScripts} is set.
   */
  @Override
  public void apply(
      final Project root,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean replaceScripts,
      final Predicate<? super CompatibilityFinding> approval) {
    if (!root.taskRunner().isRootRunner()) {
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
  @Override
  public void rollback() {
    if (workspace != null) {
      files.buildTools().rollbackWorkspace(workspace);
    }
    rules.reversed().forEach(files.ruleFiles()::rollback);
    builds.reversed().forEach(files.buildTools()::rollback);
  }
}
