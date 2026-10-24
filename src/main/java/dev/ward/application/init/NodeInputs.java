package dev.ward.application.init;

import dev.ward.domain.project.NodeSources;
import dev.ward.domain.project.PackageManifests;

/**
 * The ports init reads Node packages through, injected as one binding.
 *
 * @param manifests reads a Node project's {@code package.json}
 * @param sources finds whether a Node package holds JavaScript sources of its own
 */
public record NodeInputs(PackageManifests manifests, NodeSources sources) {}
