package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Picks the Swift tool configuration of a SwiftPM or Xcode project from what it contains. */
final class SwiftToolSelection {
  private static final int SINGLE_SCHEME = 1;

  private SwiftToolSelection() {}

  /** Builds the tools of {@code project} from its executables, Xcode metadata and sources. */
  static SwiftTools of(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final Path root = project.root();
    final SwiftPorts ports = inputs.swift();
    final boolean xcode = project.buildTool() == BuildTool.XCODE;
    return new SwiftTools(
        project.buildTool(),
        inputs.environment().inspect(root).hasExecutable(project.buildExecutable()),
        preset,
        ports.sources().of(root),
        xcode ? ports.xcode().workspace(root) : Optional.empty(),
        xcode
            ? scheme(ports.xcode().schemes(root), Optional.ofNullable(project.xcodeScheme()))
            : null,
        xcode ? (ports.xcode().macOsOnly(root) ? "macOS" : "iOS Simulator") : null,
        ports.configs().userConfig(root, SwiftTools.SWIFTLINT_CONFIG).isPresent());
  }

  /**
   * The scheme {@code requested} names, or the one shared scheme when there is only one. Several
   * schemes need a choice, and a named scheme must be one of them.
   */
  static String scheme(final List<String> schemes, final Optional<String> requested) {
    if (requested.isPresent()) {
      if (!schemes.contains(requested.get())) {
        throw new WardException(
            "No Xcode scheme named "
                + requested.get()
                + "; the project's schemes are "
                + String.join(", ", schemes)
                + ".");
      }
      return requested.get();
    }
    if (schemes.isEmpty()) {
      throw new WardException(
          "No shared Xcode scheme was found; share one in Xcode's scheme settings.");
    }
    if (schemes.size() > SINGLE_SCHEME) {
      throw new WardException(
          "The project has several Xcode schemes ("
              + String.join(", ", schemes)
              + "); choose one with --scheme.");
    }
    return schemes.getFirst();
  }
}
