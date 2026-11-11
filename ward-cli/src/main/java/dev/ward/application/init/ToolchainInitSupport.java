package dev.ward.application.init;

import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;

/** The init choices that depend on a project's toolchain. */
public interface ToolchainInitSupport extends OwnedFileSupport, MemberSupport {
  /**
   * Returns the gates that run through the toolchain's build tool, with availability resolved;
   * {@code preset} is the selected preset, or {@code null} for a custom selection or a gate list.
   */
  Gates gates(Project project, QualityPreset preset, InitInputs inputs);

  /** Returns the gate IDs {@code preset} enables for {@code project}, in configuration order. */
  List<String> presetGates(Project project, QualityPreset preset, InitInputs inputs);

  /**
   * Returns whether init handles {@code project}'s build tool; the default accepts every build tool
   * of the toolchain, and a project whose tool is not handled is reported as unsupported.
   */
  default boolean handles(final Project project) {
    return true;
  }

  /**
   * Returns whether the toolchain has gates for {@code project}'s language; the default accepts
   * every language and lets {@link SourceLanguages} name the one it lacks.
   */
  default boolean supports(final Project project, final InitInputs inputs) {
    return true;
  }

  /**
   * Returns whether init offers the opt-in {@code crap} gate for {@code project}, that is, whether
   * it can make the project's coverage tool write the report the gate reads.
   */
  default boolean offersCrap(final Project project, final InitInputs inputs) {
    return false;
  }

  /**
   * Returns {@code gates} with the setup the selected {@code crap} gate needs: it is placed after
   * the gate that produces the coverage data, and the toolchain adds the report to the commands or
   * checks. Returns {@code gates} when {@code crap} is not among them.
   *
   * @throws dev.ward.domain.config.WardException if the gate that produces the data is missing
   */
  default List<InitGateOption> withCrap(
      final Project project, final List<InitGateOption> gates, final InitInputs inputs) {
    return gates;
  }

  /** Returns the presets init offers for the toolchain, in wizard order. */
  default List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  /** Returns the gates of {@code gateIds} that the project's runtime cannot run. */
  default List<GateOmission> omissions(
      final Project project, final List<String> gateIds, final InitInputs inputs) {
    return List.of();
  }

  /** Returns the plan and output notes the toolchain adds for {@code project}. */
  default List<String> notes(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    return List.of();
  }

  /**
   * Returns the CI workflow steps that set up the toolchain, each line ending in a newline; {@code
   * manifest} is the project's {@code package.json}, empty outside Node.
   */
  String ciSetup(Project project, CiWorkflowTemplate.ProjectFacts facts);

  /** Returns the GitHub Actions runner the CI job of {@code project} runs on. */
  default String runner() {
    return "ubuntu-24.04";
  }

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
