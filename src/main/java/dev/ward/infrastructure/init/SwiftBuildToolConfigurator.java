package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftToolchainInspection;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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
    final Path directory = project.root().resolve(SwiftToolsPackage.DIRECTORY);
    final Path manifest = project.root().resolve(SwiftToolsPackage.MANIFEST);
    final Path wardDirectory = project.root().resolve(WARD_DIRECTORY);
    final boolean wardExisted = Files.isDirectory(wardDirectory);
    write(directory, manifest);
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
    return new PlannedFileChange(
        manifest,
        PlannedFileChange.Action.CREATE,
        List.of(
            "pin SwiftLintPlugins "
                + SwiftToolCatalog.SWIFTLINT_PLUGINS_VERSION
                + " in "
                + SwiftToolsPackage.MANIFEST,
            "run " + String.join(" ", SwiftToolsPackage.resolveCommand())));
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

  private static void write(final Path directory, final Path manifest) {
    try {
      Files.createDirectories(directory);
      Files.writeString(manifest, SwiftToolsPackage.manifest());
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
