package dev.ward.infrastructure.init;

import dev.ward.domain.project.Toolchain;
import dev.ward.domain.project.node.NodeToolConfigs;
import dev.ward.domain.project.node.PackageManifest;
import dev.ward.domain.project.node.PackageManifests;
import dev.ward.domain.project.python.PythonToolConfigs;
import dev.ward.infrastructure.init.node.NodeRuleFileTargets;
import dev.ward.infrastructure.init.python.PythonRuleFileTargets;
import dev.ward.infrastructure.init.swift.SwiftRuleFileTargets;
import java.util.Map;
import java.util.Optional;

/** The toolchain rule files of projects with no package manifest or tool configs of their own. */
public final class RuleFileTargetsFixtures {
  private RuleFileTargetsFixtures() {}

  /** Returns a preset writer whose toolchain targets read no manifest and no tool config. */
  public static PresetRuleFileWriter writer() {
    return new PresetRuleFileWriter(defaults());
  }

  /** Returns the Node, Python and Swift targets, reading no manifest and no tool config. */
  public static Map<Toolchain, RuleFileTargets> defaults() {
    return of(root -> PackageManifest.EMPTY, (root, gate) -> Optional.empty());
  }

  /**
   * Returns the Node, Python and Swift targets, with Node reading the given manifest and configs.
   */
  public static Map<Toolchain, RuleFileTargets> of(
      final PackageManifests manifests, final NodeToolConfigs configs) {
    return of(manifests, configs, (root, tool) -> Optional.empty());
  }

  /** Returns the Node, Python and Swift targets, reading the given manifest and tool configs. */
  public static Map<Toolchain, RuleFileTargets> of(
      final PackageManifests manifests,
      final NodeToolConfigs configs,
      final PythonToolConfigs pythonConfigs) {
    return Map.of(
        Toolchain.NODE,
        new NodeRuleFileTargets(manifests, configs),
        Toolchain.PYTHON,
        new PythonRuleFileTargets(pythonConfigs),
        Toolchain.SWIFT,
        new SwiftRuleFileTargets());
  }
}
