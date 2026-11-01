package dev.ward.infrastructure.init.python;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.RuleFileTargets;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

/** The rule files of a Python project: its tool configs, unless the project keeps its own. */
public final class PythonRuleFileTargets implements RuleFileTargets {
  private final PythonToolConfigs pythonConfigs;

  /** Creates the targets from the tool configs a Python project keeps itself. */
  @Inject
  public PythonRuleFileTargets(final PythonToolConfigs pythonConfigs) {
    this.pythonConfigs = pythonConfigs;
  }

  @Override
  public List<ConfigTarget> targets(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates)
      throws IOException {
    return PythonConfigFiles.targets(project, pythonConfigs, preset, gates);
  }
}
