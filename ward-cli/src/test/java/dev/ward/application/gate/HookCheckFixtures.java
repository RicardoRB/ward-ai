package dev.ward.application.gate;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaceMembers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        new PartialPorts(new FilePathExistence(), new NodeWorkspaceMembers())
    );
  }

  /** Returns a prompt guard with real adapters, the bundled secret rules and no result cache. */
  public static PromptGuard prompts() {
    final AnalysisPorts ports =
        new AnalysisPorts(
            new ProjectRuleSource(new ClasspathRuleSource()),
            new DeclarativeRuleMatcher(),
            new FileSystemSourceUnits(new FakeCommandExecutor(0, "", "")),
            List.of(),
            projectRoot -> AnalysisCacheFixtures.memory(new HashMap<>()),
            AnalysisCacheFixtures.codec(),
            "test",
            DependencyAdapters.none(),
            (unused, classpath) -> Map.of()
        );
    return new PromptGuard(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new SecretGates(ports, (projectRoot, since, sink) -> sink.accept(List.of()))
    );
  }
}
