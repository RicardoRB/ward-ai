package dev.ward.application.init;

import dev.ward.domain.init.BuildModulePlanning;

/**
 * The toolchain-specific read ports of the init catalog. Grouped so that {@link InitPorts} keeps
 * within the constructor parameter limit.
 *
 * @param swift reads the Xcode schemes, Swift tool configs and source directories
 * @param modulePlanning plans the module build scripts a build tool would change
 */
public record ToolchainPorts(SwiftPorts swift, BuildModulePlanning modulePlanning) {
  /** Ports for a catalog with no Swift project and no module build scripts. */
  public static final ToolchainPorts NONE =
      new ToolchainPorts(SwiftPorts.NONE, BuildModulePlanning.none());
}
