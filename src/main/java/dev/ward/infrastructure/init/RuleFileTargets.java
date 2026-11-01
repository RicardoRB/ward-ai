package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.util.List;

/**
 * The rule files one toolchain's project needs. Each toolchain implements it, and the Dagger module
 * registers the implementation under its toolchain, so the preset writer names no toolchain.
 */
public interface RuleFileTargets {
  /**
   * Returns each rule file {@code project} needs for {@code gates}, in the order init writes them.
   */
  List<ConfigTarget> targets(Project project, QualityPreset preset, List<InitGateOption> gates)
      throws IOException;
}
