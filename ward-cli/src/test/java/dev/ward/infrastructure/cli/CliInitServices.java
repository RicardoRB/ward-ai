package dev.ward.infrastructure.cli;

import dev.ward.application.init.InitProjectFiles;
import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.NoRuleFileGeneration;
import dev.ward.application.project.ProjectDetector;
import dev.ward.infrastructure.agent.ClaudeCodeIntegration;
import dev.ward.infrastructure.agent.OpenCodeIntegration;
import dev.ward.infrastructure.doctor.SystemEnvironmentInspection;
import dev.ward.infrastructure.init.ArchitectureTablesFixtures;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.util.Set;

/** Real-adapter init services for CLI integration tests. */
final class CliInitServices {
  private CliInitServices() {}

  /** An init service offering the OpenCode and Claude Code integrations, without rule files. */
  static InitService openCodeAndClaude() {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(new OpenCodeIntegration(), new ClaudeCodeIntegration()),
        new InitSetupCatalog(new SystemEnvironmentInspection(new ProcessCommandExecutor())),
        new InitProjectFiles(
            new PomToolConfigurator(),
            new NoRuleFileGeneration(),
            ArchitectureTablesFixtures.create(),
            new FileConfigurationStorage(),
            new OwnedSectionWriter()));
  }
}
