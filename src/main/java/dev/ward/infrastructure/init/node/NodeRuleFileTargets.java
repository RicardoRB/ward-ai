package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.RuleFileTargets;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

/** The rule files of a Node project: its tool configs, read through the project's manifest. */
public final class NodeRuleFileTargets implements RuleFileTargets {
  private final PackageManifests manifests;
  private final NodeToolConfigs configs;

  /** Creates the targets from the manifests and the tool configs a project keeps itself. */
  @Inject
  public NodeRuleFileTargets(final PackageManifests manifests, final NodeToolConfigs configs) {
    this.manifests = manifests;
    this.configs = configs;
  }

  @Override
  public List<ConfigTarget> targets(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates)
      throws IOException {
    return NodeConfigFiles.targets(project, manifests.read(project.root()), configs, preset, gates);
  }
}
