package dev.ward.application.init;

import dev.ward.domain.project.NodeSources;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.WorkspaceMembers;

/**
 * The ports init reads Node packages through, injected as one binding.
 *
 * @param manifests reads a Node project's {@code package.json}
 * @param sources finds whether a Node package holds JavaScript sources of its own
 * @param configs finds the tool configs a Node project keeps itself
 * @param members lists the packages a workspace root holds
 */
public record NodeInputs(
    PackageManifests manifests,
    NodeSources sources,
    NodeToolConfigs configs,
    WorkspaceMembers members) {
  /** Inputs for a project whose workspace lists no members. */
  public NodeInputs(
      final PackageManifests manifests, final NodeSources sources, final NodeToolConfigs configs) {
    this(manifests, sources, configs, WorkspaceMembers.none());
  }
}
