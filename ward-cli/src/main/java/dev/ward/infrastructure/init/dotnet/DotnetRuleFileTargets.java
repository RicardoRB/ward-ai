package dev.ward.infrastructure.init.dotnet;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.RuleFileTargets;
import javax.inject.Inject;
import java.util.List;

/** The rule files of a .NET project: none; the Ward rules replace analyzer configuration. */
public final class DotnetRuleFileTargets implements RuleFileTargets {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public DotnetRuleFileTargets() {}

  @Override
  public List<ConfigTarget> targets(
      final Project project,
      final QualityPreset preset,
      final List<InitGateOption> gates
  ) {
    return List.of();
  }
}
