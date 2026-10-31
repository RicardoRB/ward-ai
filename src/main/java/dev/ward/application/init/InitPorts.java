package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.BuildModulePlanning;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.PythonToolConfigs;

/**
 * The read ports the init catalog needs, injected as one binding.
 *
 * @param environment resolves which executables are on the path
 * @param node reads Node packages
 * @param buildTools reads the runtime JDK and the gates it cannot run
 * @param python finds the tool configs a Python project keeps itself
 * @param modulePlanning plans the module build scripts a build tool would change
 */
public record InitPorts(
    EnvironmentInspection environment,
    NodeInputs node,
    BuildToolConfiguration buildTools,
    PythonToolConfigs python,
    BuildModulePlanning modulePlanning) {
  /** Creates the ports for a catalog that reads no Python project's configs. */
  public InitPorts(
      final EnvironmentInspection environment,
      final NodeInputs node,
      final BuildToolConfiguration buildTools) {
    this(
        environment,
        node,
        buildTools,
        (root, tool) -> java.util.Optional.empty(),
        BuildModulePlanning.none());
  }
}
