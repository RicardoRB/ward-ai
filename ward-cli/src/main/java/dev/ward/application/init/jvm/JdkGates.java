package dev.ward.application.init.jvm;

import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.InitToolchains;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;

/** Which gates a project's runtime JDK cannot run, and that JDK, as init reads them. */
public final class JdkGates {
  private final InitInputs inputs;

  JdkGates(final InitInputs inputs) {
    this.inputs = inputs;
  }

  /** Returns the JDK facts {@code catalog} reads. */
  public static JdkGates of(final InitGeneralCatalog catalog) {
    return new JdkGates(catalog.inputs);
  }

  /** Returns the gates of {@code gateIds} that {@code project}'s runtime JDK cannot run. */
  public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return inputs.omissions(project, gateIds);
  }

  /**
   * Returns the gates {@code preset} enables that {@code project}'s runtime JDK cannot run; the
   * catalog's preset gates leave them out.
   */
  public List<GateOmission> presetOmissions(final Project project, final QualityPreset preset) {
    return omissions(project, InitToolchains.support(project).presetGates(project, preset, inputs));
  }

  /** Returns the JDK that runs {@code project}'s build, when it can be read. */
  public Optional<Integer> runtimeJdk(final Project project) {
    return inputs.runtimeJdk(project);
  }
}
