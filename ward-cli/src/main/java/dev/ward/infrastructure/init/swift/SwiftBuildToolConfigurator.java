package dev.ward.infrastructure.init.swift;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.domain.project.swift.SwiftToolchainInspection;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

/**
 * Stops Swift init on a machine or project it cannot set up. Swift writes no build file: the
 * project's {@code Package.swift} and Xcode project are never edited, and the gates need only the
 * build tool that is already installed.
 */
public final class SwiftBuildToolConfigurator implements BuildToolConfiguration {
  private static final String PACKAGE_MANIFEST = "Package.swift";
  private final SwiftToolchainInspection toolchain;

  /** Creates the guard; {@code toolchain} reads the machine's Xcode and platform. */
  @Inject
  public SwiftBuildToolConfigurator(final SwiftToolchainInspection toolchain) {
    this.toolchain = toolchain;
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    final List<String> problems = SwiftCompatibility.problems(project, toolchain);
    if (!problems.isEmpty()) {
      throw new WardException(problems.getFirst());
    }
    return new BuildFileChange(Toolchain.SWIFT, null, null, List.of());
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final Path manifest = project.root().resolve(PACKAGE_MANIFEST);
    final List<String> problems = SwiftCompatibility.problems(project, toolchain);
    if (!problems.isEmpty()) {
      return new PlannedFileChange(manifest, PlannedFileChange.Action.CONFLICT, problems);
    }
    return new PlannedFileChange(manifest, PlannedFileChange.Action.UNCHANGED, List.of());
  }

  @Override
  public void rollback(final BuildFileChange change) {
    // Swift init writes no build file, so there is nothing to undo.
  }
}
