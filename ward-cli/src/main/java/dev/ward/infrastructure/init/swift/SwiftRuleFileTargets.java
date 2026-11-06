package dev.ward.infrastructure.init.swift;

import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.RuleFileTargets;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

/** The rule files of a Swift project: its SwiftLint and swift-format configs. */
public final class SwiftRuleFileTargets implements RuleFileTargets {
  /** Creates the targets; Swift needs no input beyond the project itself. */
  @Inject
  public SwiftRuleFileTargets() {}

  @Override
  public List<ConfigTarget> targets(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates)
      throws IOException {
    final boolean wardFormat =
        gates.stream().anyMatch(gate -> FormatGates.WARD_FORMAT.equals(gate.id()));
    return SwiftConfigFiles.targets(project, preset, !wardFormat);
  }
}
