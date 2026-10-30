package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.project.NodeSources;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import dev.ward.domain.project.WorkspaceMembers;
import java.util.List;
import java.util.Optional;

/**
 * What a toolchain support reads to offer gates.
 *
 * @param environment resolves which executables are on the path
 * @param manifests reads a Node project's {@code package.json}
 * @param sources finds whether a Node package holds JavaScript sources of its own
 * @param configs finds the tool configs a Node project keeps itself
 * @param buildTools reads which gates the project's runtime JDK cannot run, when available
 * @param pythonConfigs finds the tool configs a Python project keeps itself
 * @param members lists the packages a Node workspace root holds
 */
record InitInputs(
    EnvironmentInspection environment,
    PackageManifests manifests,
    NodeSources sources,
    NodeToolConfigs configs,
    Optional<BuildToolConfiguration> buildTools,
    PythonToolConfigs pythonConfigs,
    WorkspaceMembers members) {
  /** Inputs that read no Python config of their own. */
  InitInputs(
      final EnvironmentInspection environment,
      final PackageManifests manifests,
      final NodeSources sources,
      final NodeToolConfigs configs,
      final Optional<BuildToolConfiguration> buildTools) {
    this(
        environment,
        manifests,
        sources,
        configs,
        buildTools,
        (root, tool) -> Optional.empty(),
        WorkspaceMembers.none());
  }

  /** Inputs that do not read the runtime JDK. */
  InitInputs(
      final EnvironmentInspection environment,
      final PackageManifests manifests,
      final NodeSources sources,
      final NodeToolConfigs configs) {
    this(environment, manifests, sources, configs, Optional.empty());
  }

  /** Inputs for projects that keep no Node tool config of their own. */
  InitInputs(
      final EnvironmentInspection environment,
      final PackageManifests manifests,
      final NodeSources sources) {
    this(environment, manifests, sources, (root, gate) -> Optional.empty());
  }

  /** Inputs for a catalog that reads no Node package. */
  static InitInputs withoutManifests(final EnvironmentInspection environment) {
    return new InitInputs(
        environment,
        root -> PackageManifest.EMPTY,
        root -> false,
        (root, gate) -> Optional.empty());
  }

  /** Inputs that read Node packages through {@code node}. */
  static InitInputs of(final EnvironmentInspection environment, final NodeInputs node) {
    return new InitInputs(
        environment,
        node.manifests(),
        node.sources(),
        node.configs(),
        Optional.empty(),
        (root, tool) -> Optional.empty(),
        node.members());
  }

  /** Inputs that also read the runtime JDK through the build-file adapters of {@code ports}. */
  static InitInputs of(final InitPorts ports) {
    final NodeInputs node = ports.node();
    return new InitInputs(
        ports.environment(),
        node.manifests(),
        node.sources(),
        node.configs(),
        Optional.of(ports.buildTools()),
        ports.python(),
        node.members());
  }

  /** Returns the gates of {@code gateIds} the project's runtime JDK cannot run. */
  List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return buildTools.map(tools -> tools.omissions(project, gateIds)).orElse(List.of());
  }

  /** Returns the JDK that runs the project's build, when it can be read. */
  Optional<Integer> runtimeJdk(final Project project) {
    return buildTools.flatMap(tools -> tools.runtimeJdk(project));
  }
}
