package dev.ward.application.init.swift;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.PartialCheckDefaults.Table;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport;
import dev.ward.domain.init.ArchitectureGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Init choices for SwiftPM packages and Xcode projects with Swift sources. Tuist and XcodeGen
 * projects are not handled.
 */
public final class SwiftInitSupport implements ToolchainInitSupport {
  private static final Map<Language, SourceLanguageGates<SwiftTools>> LANGUAGES =
      Map.of(Language.SWIFT, new SwiftGates());

  @Override
  public Optional<String> gitignoreEntries(
      final Project project, final List<InitGateOption> gates) {
    // Harmonize builds its own package, whose build output is Git-ignored only when it is checked.
    return Optional.of(
        ".ward/reports/\n.ward/swift-tools/.build/\n"
            + (gates.stream().anyMatch(gate -> SwiftTools.HARMONIZE.equals(gate.id()))
                ? ArchitectureGates.HARMONIZE_PACKAGE + "/.build/\n"
                : ""));
  }

  @Override
  public Set<String> partialGates() {
    return SwiftPartialTables.GATES;
  }

  @Override
  public Optional<Table> partialTable(
      final String gate,
      final Project project,
      final QualityPreset preset,
      final List<String> command) {
    return SwiftPartialTables.forGate(gate, project, command);
  }

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final SwiftTools swift = SwiftToolSelection.of(project, preset, inputs);
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
  public List<String> agentRules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    return SwiftAgentRules.lines(gates);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return SwiftCiSetup.steps(project, facts.harmonize());
  }

  /** Swift builds need macOS, and Xcode 26 is the default on the macos-26 image. */
  @Override
  public String runner() {
    return SwiftCiSetup.RUNNER;
  }
}
