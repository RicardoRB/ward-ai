package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    final Path file = project.root().resolve(PACKAGE_JSON);
    final PackageManifest manifest = manifests.read(project.root());
    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(project.language(), gates, manifest);
    final List<String> warnings = new ArrayList<>(bunfigWarning(project, gates));
    if (missing.isEmpty()) {
      return new BuildFileChange(Toolchain.NODE, null, null, List.of(), warnings);
    }
    final NodeCompatibility compatibility = NodeCompatibility.check(project, manifest, missing);
    if (compatibility.incompatible()) {
      throw new WardException(compatibility.message());
    }
    warnings.addAll(compatibility.notes());
    final String original = read(file);
    final String edited = PackageJsonEdit.addDevDependencies(original, versions(missing));
    final Path lock = lockFile(project);
    final BuildFileChange change =
        new BuildFileChange(
            Toolchain.NODE,
            file,
            original,
            missing.stream().map(NodePackageCatalog.Package::name).toList(),
            warnings,
            lock,
            Files.isRegularFile(lock) ? read(lock) : null);
    write(file, edited);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=node-dependencies-added count="
                + missing.size()
                + " manager="
                + project.buildExecutable());
    install(project, change);
    return change;
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final Path file = project.root().resolve(PACKAGE_JSON);
    final PackageManifest manifest = manifests.read(project.root());
    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(project.language(), gates, manifest);
    if (missing.isEmpty()) {
      return new PlannedFileChange(
          file, PlannedFileChange.Action.UNCHANGED, bunfigWarning(project, gates));
    }
    final NodeCompatibility compatibility = NodeCompatibility.check(project, manifest, missing);
    if (compatibility.incompatible()) {
      return new PlannedFileChange(
          file,
          PlannedFileChange.Action.CONFLICT,
          compatibility.findings().stream()
              .map(finding -> "INCOMPATIBLE " + finding.artifactId() + ": " + finding.message())
              .toList());
    }
    final List<String> details = new ArrayList<>();
    missing.forEach(tool -> details.add("add devDependency " + tool.name() + " " + tool.version()));
    details.addAll(compatibility.notes());
    details.addAll(bunfigWarning(project, gates));
    details.add(
        "run "
            + String.join(" ", installCommand(project))
            + ", which updates "
            + project.root().relativize(lockFile(project)));
    return new PlannedFileChange(file, PlannedFileChange.Action.MODIFY, details);
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || change.file() == null) {
      return;
    }
    LOGGER.log(Level.INFO, "event=node-rollback");
    write(change.file(), change.originalContent());
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
      write(lock, change.lockOriginal());
    }
  }

  private void install(final Project project, final BuildFileChange change) {
    final List<String> command = installCommand(project);
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

  /** Bun reads its coverage threshold only from bunfig.toml, which init never edits. */
  private static List<String> bunfigWarning(
      final Project project, final List<InitGateOption> gates) {
    final Path bunfig = project.root().resolve(NodeConfigFiles.BUNFIG);
    if (gates.stream().noneMatch(gate -> "bun-test".equals(gate.id()))
        || !Files.isRegularFile(bunfig)
        || NodeConfigFiles.owned(read(bunfig))) {
      return List.of();
    }
    return List.of(
        "bunfig.toml is kept as you wrote it; bun-test enforces a coverage threshold only if it"
            + " sets [test] coverageThreshold.");
  }

  private static List<String> installCommand(final Project project) {
    return List.of(project.buildExecutable(), "install");
  }

  /** The lock file the install writes; Bun keeps a binary {@code bun.lockb} it already has. */
  private static Path lockFile(final Project project) {
    final Path root = project.root();
    return switch (project.buildTool()) {
      case PNPM -> root.resolve("pnpm-lock.yaml");
      case YARN -> root.resolve("yarn.lock");
      case BUN ->
          Files.isRegularFile(root.resolve("bun.lockb"))
              ? root.resolve("bun.lockb")
              : root.resolve("bun.lock");
      default -> root.resolve("package-lock.json");
    };
  }

  private static Map<String, String> versions(final List<NodePackageCatalog.Package> packages) {
    final Map<String, String> versions = new LinkedHashMap<>();
    packages.forEach(tool -> versions.put(tool.name(), tool.version()));
    return versions;
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }

  private static void write(final Path file, final String content) {
    try {
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new WardException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }
}
