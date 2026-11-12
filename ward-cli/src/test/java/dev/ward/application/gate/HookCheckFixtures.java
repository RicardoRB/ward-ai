package dev.ward.application.gate;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaceMembers;

/** Wires real project detection and configuration around a test executor. */
public final class HookCheckFixtures {
  private HookCheckFixtures() {}

  /** Returns a check service with real adapters, running commands through {@code executor}. */
  public static CheckService checks(final CommandExecutor executor) {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor),
        new QualityGateRunner(),
        new PartialPorts(new FilePathExistence(), new NodeWorkspaceMembers()));
  }
}
