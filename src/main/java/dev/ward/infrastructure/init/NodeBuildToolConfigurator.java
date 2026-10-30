package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.TurboWorkspace;
import dev.ward.domain.init.TurboWorkspaceChange;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.NodeSupport;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Adds the dev dependencies the selected Node gates need to {@code package.json}, then installs
 * them. A failed install restores {@code package.json} and the lock file.
 */
public final class NodeBuildToolConfigurator implements BuildToolConfiguration {
  private static final Logger LOGGER = Logger.getLogger(NodeBuildToolConfigurator.class.getName());
  private static final String PACKAGE_JSON = "package.json";
  private final PackageManifests manifests;
  private final CommandExecutor executor;

  /** Creates the adapter; {@code executor} runs the package manager's install. */
  @Inject
  public NodeBuildToolConfigurator(
      final PackageManifests manifests, final CommandExecutor executor) {
    this.manifests = manifests;
    this.executor = executor;
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return write(project, gates);
  }

  private BuildFileChange write(final Project project, final List<InitGateOption> gates) {
    final Path file = project.root().resolve(PACKAGE_JSON);
    final PackageManifest manifest = manifests.read(project.root());
    final List<NodePackageCatalog.Package> missing =
        NodeManifestFiles.missing(project, gates, manifest);
    final List<String> warnings = new ArrayList<>(NodeSupport.warning(project).stream().toList());
    warnings.addAll(NodeManifestFiles.bunfigWarning(project, gates));
    final NodeCompatibility compatibility =
        NodeCompatibility.check(
            project,
            manifest,
            missing,
            gates.stream().map(InitGateOption::id).collect(Collectors.toSet()));
    if (compatibility.incompatible()) {
      throw new WardException(compatibility.message());
    }
    warnings.addAll(compatibility.notes());
    if (missing.isEmpty()) {
      return new BuildFileChange(Toolchain.NODE, null, null, List.of(), warnings);
    }
    final String original = NodeManifestFiles.read(file);
    final String edited =
        PackageJsonEdit.addDevDependencies(original, NodeManifestFiles.versions(missing));
    final Path lock = NodeInstall.lockFile(project);
    final BuildFileChange change =
        new BuildFileChange(
            Toolchain.NODE,
            file,
            original,
            missing.stream().map(NodePackageCatalog.Package::name).toList(),
            warnings,
            lock,
            Files.isRegularFile(lock) ? NodeManifestFiles.read(lock) : null);
    NodeManifestFiles.writeText(file, edited);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=node-dependencies-added count="
                + missing.size()
                + " manager="
                + project.buildExecutable());
    if (installs(project)) {
      install(project, change);
    }
    return change;
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return describe(project, gates);
  }

  private PlannedFileChange describe(final Project project, final List<InitGateOption> gates) {
    final Path file = project.root().resolve(PACKAGE_JSON);
    final List<String> nodeWarning = NodeSupport.warning(project).stream().toList();
    final PackageManifest manifest = manifests.read(project.root());
    final List<NodePackageCatalog.Package> missing =
        NodeManifestFiles.missing(project, gates, manifest);
    final NodeCompatibility compatibility =
        NodeCompatibility.check(
            project,
            manifest,
            missing,
            gates.stream().map(InitGateOption::id).collect(Collectors.toSet()));
    if (compatibility.incompatible()) {
      final List<String> details = new ArrayList<>(nodeWarning);
      compatibility
          .findings()
          .forEach(
              finding ->
                  details.add("INCOMPATIBLE " + finding.artifactId() + ": " + finding.message()));
      return new PlannedFileChange(file, PlannedFileChange.Action.CONFLICT, details);
    }
    if (missing.isEmpty()) {
      final List<String> details = new ArrayList<>(nodeWarning);
      details.addAll(NodeManifestFiles.bunfigWarning(project, gates));
      return new PlannedFileChange(file, PlannedFileChange.Action.UNCHANGED, details);
    }
    final List<String> details = new ArrayList<>(nodeWarning);
    missing.forEach(tool -> details.add("add devDependency " + tool.name() + " " + tool.version()));
    details.addAll(compatibility.notes());
    details.addAll(NodeManifestFiles.bunfigWarning(project, gates));
    if (installs(project)) {
      details.add(
          "run "
              + String.join(" ", NodeInstall.installCommand(project))
              + ", which updates "
              + project.root().relativize(NodeInstall.lockFile(project)));
    }
    return new PlannedFileChange(file, PlannedFileChange.Action.MODIFY, details);
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || change.file() == null) {
      return;
    }
    LOGGER.log(Level.INFO, "event=node-rollback");
    NodeManifestFiles.writeText(change.file(), change.originalContent());
    final Path lock = change.lockFile();
    if (lock == null) {
      return;
    }
    if (change.lockOriginal() == null) {
      try {
        Files.deleteIfExists(lock);
      } catch (IOException e) {
        throw new WardException("Could not remove " + lock + ": " + e.getMessage(), e);
      }
    } else {
      NodeManifestFiles.writeText(lock, change.lockOriginal());
    }
  }

  @Override
  public TurboWorkspaceChange applyWorkspace(
      final TurboWorkspace workspace, final boolean replaceScripts) {
    final TurborepoWriter.Result result = TurborepoWriter.apply(workspace, replaceScripts);
    return new TurboWorkspaceChange(
        result.written().stream()
            .map(
                written -> new TurboWorkspaceChange.WrittenFile(written.file(), written.original()))
            .toList(),
        result.kept());
  }

  @Override
  public void rollbackWorkspace(final TurboWorkspaceChange change) {
    TurborepoWriter.rollback(
        change.written().stream()
            .map(written -> new TurborepoWriter.Written(written.file(), written.original()))
            .toList());
  }

  /** A Turborepo member's dependencies are installed by its workspace root, once. */
  private static boolean installs(final Project project) {
    return project.taskRunner() != TaskRunner.TURBO_MEMBER;
  }

  private void install(final Project project, final BuildFileChange change) {
    final List<String> command = NodeInstall.installCommand(project);
    LOGGER.log(Level.INFO, () -> "event=node-install-start manager=" + project.buildExecutable());
    final CommandResult result = executor.execute(command, project.root());
    LOGGER.log(Level.INFO, () -> "event=node-install-complete exit=" + result.exitCode());
    if (result.exitCode() != 0) {
      rollback(change);
      throw new WardException(
          "`"
              + String.join(" ", command)
              + "` failed with exit code "
              + result.exitCode()
              + "; package.json and "
              + change.lockFile().getFileName()
              + " were restored.");
    }
  }
}
