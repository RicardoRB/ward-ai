package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.project.NodeSources;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;
import java.util.Optional;

/**
 * What a toolchain support reads to offer gates.
 *
 * @param environment resolves which executables are on the path
 * @param manifests reads a Node project's {@code package.json}
 * @param sources finds whether a Node package holds JavaScript sources of its own
 * @param configs finds the tool configs a Node project keeps itself
 */
record InitInputs(
    EnvironmentInspection environment,
    PackageManifests manifests,
    NodeSources sources,
    NodeToolConfigs configs) {
  /** Inputs for projects that keep no Node tool config of their own. */
  InitInputs(
      final EnvironmentInspection environment,
      final PackageManifests manifests,
      final NodeSources sources) {
    this(environment, manifests, sources, (root, gate) -> Optional.empty());
  }

  /** Inputs for a catalog that reads no Node package. */
  static InitInputs withoutManifests(final EnvironmentInspection environment) {
    return new InitInputs(environment, root -> PackageManifest.EMPTY, root -> false);
  }

  /** Inputs that read Node packages through {@code node}. */
  static InitInputs of(final EnvironmentInspection environment, final NodeInputs node) {
    return new InitInputs(environment, node.manifests(), node.sources(), node.configs());
  }
}
