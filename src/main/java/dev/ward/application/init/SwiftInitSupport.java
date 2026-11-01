package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Init choices for SwiftPM packages and Xcode projects with Swift sources. Tuist and XcodeGen
 * projects are not handled.
 */
final class SwiftInitSupport implements ToolchainInitSupport {
  private static final Map<Language, SourceLanguageGates<SwiftTools>> LANGUAGES =
      Map.of(Language.SWIFT, new SwiftGates());
  private static final int SINGLE_SCHEME = 1;

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final SwiftTools swift = tools(project, preset, inputs);
    return new Gates(SourceLanguages.of(LANGUAGES, project).analysisGates(swift), List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    return SourceLanguages.of(LANGUAGES, project).presetGates(preset).stream()
        .filter(gate -> SwiftGates.appliesTo(project.buildTool(), gate))
        .toList();
  }

  @Override
  public boolean handles(final Project project) {
    return project.buildTool() == BuildTool.SWIFTPM || project.buildTool() == BuildTool.XCODE;
  }

  @Override
  public boolean supports(final Project project, final InitInputs inputs) {
    return project.language() == Language.SWIFT && handles(project);
  }

  @Override
  public List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return SwiftCiSetup.steps(project);
  }

  /** Swift builds need macOS, and Xcode 26 is the default on the macos-26 image. */
  @Override
  public String runner() {
    return SwiftCiSetup.RUNNER;
  }

  /** Builds the tools of {@code project} from its executables, Xcode metadata and sources. */
  private static SwiftTools tools(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
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
  private static String scheme(final List<String> schemes, final Optional<String> requested) {
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
