package dev.ward.infrastructure.init.swift;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftToolchainInspection;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.swift.SwiftToolCatalog.SwiftToolRow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Writes Ward's SwiftLint tools package and resolves it with {@code swift package resolve}. The
 * project's {@code Package.swift} and Xcode project are never edited; a failed resolve removes
 * {@code .ward/swift-tools} and nothing else.
 */
public final class SwiftBuildToolConfigurator implements BuildToolConfiguration {
  private static final Logger LOGGER = Logger.getLogger(SwiftBuildToolConfigurator.class.getName());
  private static final String WARD_DIRECTORY = ".ward";
  private final CommandExecutor executor;
  private final SwiftToolchainInspection toolchain;

  /** Creates the adapter; {@code executor} runs the resolve and {@code toolchain} checks Xcode. */
  @Inject
  public SwiftBuildToolConfigurator(
      final CommandExecutor executor, final SwiftToolchainInspection toolchain) {
    this.executor = executor;
    this.toolchain = toolchain;
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    final List<String> problems = SwiftCompatibility.problems(project, toolchain);
    if (!problems.isEmpty()) {
      throw new WardException(problems.getFirst());
    }
    final Optional<SwiftToolRow> swiftLint = swiftLint();
    if (swiftLint.isEmpty()) {
      LOGGER.log(Level.INFO, "event=swift-tools-skipped");
      return new BuildFileChange(Toolchain.SWIFT, null, null, List.of());
    }
    final Path directory = project.root().resolve(SwiftToolsPackage.DIRECTORY);
    final Path manifest = project.root().resolve(SwiftToolsPackage.MANIFEST);
    final Path wardDirectory = project.root().resolve(WARD_DIRECTORY);
    final boolean wardExisted = Files.isDirectory(wardDirectory);
    write(directory, manifest, swiftLint.get().version());
    LOGGER.log(Level.INFO, "event=swift-tools-resolve-start");
    final CommandResult result =
        executor.execute(SwiftToolsPackage.resolveCommand(), project.root());
    LOGGER.log(Level.INFO, () -> "event=swift-tools-resolve-complete exit=" + result.exitCode());
    if (result.hasExecutionError() || !result.succeeded()) {
      deleteDirectory(directory);
      if (!wardExisted) {
        deleteEmptyDirectory(wardDirectory);
      }
      throw new WardException(
          "Ward could not resolve its SwiftLint tools package, so no files were changed. Check the"
              + " network connection and run ward init again.");
    }
    return new BuildFileChange(
        Toolchain.SWIFT, manifest, null, List.of(SwiftToolCatalog.SWIFTLINT_PLUGINS_URL));
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final Path manifest = project.root().resolve(SwiftToolsPackage.MANIFEST);
    final List<String> problems = SwiftCompatibility.problems(project, toolchain);
    if (!problems.isEmpty()) {
      return new PlannedFileChange(manifest, PlannedFileChange.Action.CONFLICT, problems);
    }
    final Optional<SwiftToolRow> swiftLint = swiftLint();
    if (swiftLint.isEmpty()) {
      return new PlannedFileChange(
          manifest,
          PlannedFileChange.Action.UNCHANGED,
          List.of("no SwiftLint tools package is written for this Xcode"));
    }
    return new PlannedFileChange(
        manifest,
        PlannedFileChange.Action.CREATE,
        List.of(
            "pin SwiftLintPlugins "
                + swiftLint.get().version()
                + " in "
                + SwiftToolsPackage.MANIFEST,
            "run " + String.join(" ", SwiftToolsPackage.resolveCommand())));
  }

  /**
   * Returns the gates of {@code gateIds} whose tool has no row for the selected Xcode, so init
   * leaves them out with a plan note. Nothing is omitted when the Xcode cannot be read.
   */
  @Override
  public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    final String xcode = toolchain.xcodeVersion().orElse("");
    if (xcode.isEmpty()) {
      return List.of();
    }
    return SwiftToolCatalog.tools().stream()
        .filter(gateIds::contains)
        .filter(tool -> SwiftToolCatalog.forXcode(tool, xcode).isEmpty())
        .map(tool -> new GateOmission(tool, SwiftToolCatalog.unprovenReason(tool, xcode)))
        .toList();
  }

  /** Returns the SwiftLint row for the selected Xcode, or empty when none covers it. */
  private Optional<SwiftToolRow> swiftLint() {
    return SwiftToolCatalog.forXcode(
        SwiftToolCatalog.SWIFTLINT, toolchain.xcodeVersion().orElse(""));
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || change.file() == null) {
      return;
    }
    LOGGER.log(Level.INFO, "event=swift-rollback");
    final Path directory = change.file().getParent();
    if (directory != null) {
      deleteDirectory(directory);
    }
  }

  /** Removes {@code directory} only when it is empty, so no other file is touched. */
  private static void deleteEmptyDirectory(final Path directory) {
    try {
      Files.deleteIfExists(directory);
    } catch (IOException e) {
      LOGGER.log(
          Level.FINE, () -> "event=swift-cleanup-skipped reason=" + e.getClass().getSimpleName());
    }
  }

  private static void write(final Path directory, final Path manifest, final String version) {
    try {
      Files.createDirectories(directory);
      Files.writeString(manifest, SwiftToolsPackage.manifest(version));
    } catch (IOException e) {
      throw new WardException("Could not write " + manifest + ": " + e.getMessage(), e);
    }
  }

  /** Deletes {@code directory} and its contents; Ward owns the whole directory. */
  private static void deleteDirectory(final Path directory) {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      throw new WardException("Could not remove " + directory + ": " + e.getMessage(), e);
    }
  }
}
