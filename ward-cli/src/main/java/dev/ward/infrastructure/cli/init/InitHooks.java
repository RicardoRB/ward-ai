package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.HookPlacement;
import dev.ward.domain.init.QualityHookChange;
import dev.ward.domain.project.Project;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs the quality hooks on their own, in the directory Git runs or in a shared folder, without
 * touching {@code ward.toml}. It asks before pointing {@code core.hooksPath}.
 */
final class InitHooks {
  private static final Logger LOGGER = Logger.getLogger(InitHooks.class.getName());

  private final InitSetupCatalog catalog;
  private final InitConfirmations confirmations;
  private final PrintWriter out;
  private final Mode mode;

  /**
   * How the run behaves.
   *
   * @param dryRun only print what would change
   * @param interactive whether questions can be asked
   * @param yes whether unanswered questions take their default and an unset hooks path is set
   */
  record Mode(boolean dryRun, boolean interactive, boolean yes) {}

  private InitHooks(
      final InitSetupCatalog catalog,
      final InitConfirmations confirmations,
      final PrintWriter out,
      final Mode mode
  ) {
    this.catalog = catalog;
    this.confirmations = confirmations;
    this.out = out;
    this.mode = mode;
  }

  /** Creates the run; questions are asked only on a terminal without {@code yes}. */
  static InitHooks create(
      final InitSetupCatalog catalog,
      final InitConfirmations confirmations,
      final boolean dryRun,
      final boolean terminal,
      final boolean yes
  ) {
    return new InitHooks(
        catalog,
        confirmations,
        confirmations.out(),
        new Mode(dryRun, terminal && !yes, yes)
    );
  }

  /** Whether every project already has a {@code ward.toml}. */
  static boolean allConfigured(final InitService init, final List<Project> projects) {
    return !projects.isEmpty() && projects.stream().allMatch(init::configurationExists);
  }

  /** Removes Ward's hook sections, or with {@code dryRun} lists the files it would change. */
  static void remove(
      final InitSetupCatalog catalog,
      final List<Project> projects,
      final boolean dryRun,
      final PrintWriter out
  ) {
    for (final Project project : projects) {
      final IntegrationResult result = catalog.removeQualityHooks(project, dryRun);
      out.println(result.message());
      if (dryRun) {
        result.changed().forEach(file -> out.println("  " + file));
      }
    }
  }

  /** Plans every project first, so a conflict changes nothing, then applies them in order. */
  void run(final List<Project> projects, final String folder) {
    final List<HookPlacement> placements = new ArrayList<>();
    for (final Project project : projects) {
      final HookPlacement placement = placement(project, folder);
      final List<PlannedFileChange> plan = catalog.planQualityHooks(project, placement);
      conflict(plan);
      placements.add(placement);
      if (mode.dryRun()) {
        print(project, plan);
      }
    }
    if (mode.dryRun()) {
      placements.stream().filter(HookPlacement::isShared).findFirst().ifPresent(this::teammates);
      return;
    }
    final List<QualityHookChange> applied = new ArrayList<>();
    try {
      for (int index = 0; index < projects.size(); index++) {
        final QualityHookChange change =
            catalog.installQualityHooks(projects.get(index), placements.get(index));
        applied.add(change);
        out.println(change.result().message());
        change.result().changed().forEach(file -> out.println("  " + file));
      }
    } catch (WardException | IllegalStateException | java.io.UncheckedIOException failure) {
      applied.reversed().forEach(catalog::rollbackQualityHooks);
      throw failure;
    }
    placements.stream().filter(HookPlacement::isShared).findFirst().ifPresent(this::teammates);
  }

  private void teammates(final HookPlacement placement) {
    out.println("Teammates run once after cloning: " + placement.teammateCommand());
  }

  private void print(final Project project, final List<PlannedFileChange> plan) {
    final InitResultPrinter printer = new InitResultPrinter(out);
    plan.stream()
        .filter(file -> file.action() != PlannedFileChange.Action.UNCHANGED)
        .forEach(file -> printer.printFile(file, project.root()));
    if (plan.stream().allMatch(file -> file.action() == PlannedFileChange.Action.UNCHANGED)) {
      out.println("No files would change.");
    }
  }

  private static void conflict(final List<PlannedFileChange> plan) {
    plan.stream()
        .filter(file -> file.action() == PlannedFileChange.Action.CONFLICT)
        .findFirst()
        .ifPresent(
            file -> {
              throw new WardException(
                  String.join("; ", file.details()) + ". No files were changed."
              );
            }
        );
  }

  private HookPlacement placement(final Project project, final String folder) {
    if (folder == null) {
      return HookPlacement.GIT_RUNS;
    }
    final HookPlacement shared;
    try {
      shared = HookPlacement.shared(folder, false);
    } catch (IllegalArgumentException invalid) {
      throw new WardException(invalid.getMessage(), invalid);
    }
    final Optional<String> current = catalog.hooksPath(project);
    final boolean set = !mode.dryRun() && setHooksPath(shared.sharedFolder(), current);
    LOGGER.log(Level.INFO, () -> "event=shared-hooks hooks-path-set=" + set);
    return HookPlacement.shared(folder, set);
  }

  /** Never replaces a different value unasked; {@code --yes} only fills an unset one. */
  private boolean setHooksPath(final String folder, final Optional<String> current) {
    if (current.filter(folder::equals).isPresent()) {
      return false;
    }
    if (current.isPresent()) {
      return mode.interactive() && confirmations.replaceHooksPath(current.get(), folder);
    }
    return mode.yes() || mode.interactive() && confirmations.setHooksPath(folder);
  }
}
