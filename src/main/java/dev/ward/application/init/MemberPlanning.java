package dev.ward.application.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;

/**
 * The per-project planning a toolchain's members reuse: the build file and rule files each member
 * would get, as the root's planner computes them.
 */
public interface MemberPlanning {
  /** Returns the build-file changes of {@code project}, then of each module that would change. */
  List<PlannedFileChange> buildFiles(
      Project project, List<InitGateOption> gates, QualityPreset preset, boolean approved);

  /** Returns the rule-file changes of {@code project}. */
  List<PlannedFileChange> ruleFiles(
      Project project, QualityPreset preset, List<InitGateOption> gates);

  /** Returns the inputs the planner reads the project through. */
  InitInputs inputs();
}
