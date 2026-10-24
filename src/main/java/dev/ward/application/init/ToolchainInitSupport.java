package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.List;

/** The init choices that depend on a project's toolchain. */
interface ToolchainInitSupport {
  /**
   * Returns the gates that run through the toolchain's build tool, with availability resolved;
   * {@code preset} is the selected preset, or {@code null} for a custom selection or a gate list.
   */
  Gates gates(Project project, QualityPreset preset, InitInputs inputs);

  /** Returns the gate IDs {@code preset} enables for {@code project}, in configuration order. */
  List<String> presetGates(Project project, QualityPreset preset, InitInputs inputs);

  /**
   * Returns whether the toolchain has gates for {@code project}'s language; the default accepts
   * every language and lets {@link SourceLanguages} name the one it lacks.
   */
  default boolean supports(final Project project, final InitInputs inputs) {
    return true;
  }

  /** Returns the presets init offers for the toolchain, in wizard order. */
  List<QualityPreset> presets();

  /**
   * Returns the CI workflow steps that set up the toolchain, each line ending in a newline; {@code
   * manifest} is the project's {@code package.json}, empty outside Node.
   */
  String ciSetup(Project project, PackageManifest manifest);

  /**
   * Build-tool gates in wizard order, split around the toolchain-independent binary-tool gates.
   *
   * @param analysis the gates that build, test and analyze the project
   * @param policy the gates that enforce build, license, compatibility or custom policies
   */
  record Gates(List<InitGateOption> analysis, List<InitGateOption> policy) {
    /** Copies the lists so the gates stay immutable. */
    public Gates {
      analysis = List.copyOf(analysis);
      policy = List.copyOf(policy);
    }
  }
}
