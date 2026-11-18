package dev.ward.infrastructure.init.rust;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.RuleFileTargets;
import java.util.List;
import javax.inject.Inject;

/** The rule files of a Rust project: none, since earlier inits wrote no Rust tool files. */
public final class RustRuleFileTargets implements RuleFileTargets {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public RustRuleFileTargets() {}

  @Override
  public List<ConfigTarget> targets(
      final Project project,
      final QualityPreset preset,
      final List<InitGateOption> gates
  ) {
    return List.of();
  }
}
