package dev.ward.application.gate;

import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import dev.ward.infrastructure.analysis.swift.SwiftDependencyAdapters;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Runs the {@code ward} gate over a project with no bundled rule, so only the rules of {@code
 * ward-architecture.toml} and the tables of {@code ward.toml} are in the run.
 */
final class ArchitectureGateRun {

  private ArchitectureGateRun() {}

  /** Runs the gate of a default {@code ward} check in {@code root}. */
  static GateResult run(final Path root) {
    return run(root, new GateConfiguration("ward", true, List.of()));
  }

  /** Runs the gate of {@code check} in {@code root}. */
  static GateResult run(final Path root, final GateConfiguration check) {
    final AnalysisPorts ports =
        new AnalysisPorts(
            new ProjectRuleSource(new ClasspathRuleSource(List.of())),
            BundledRuleMatchers.create(),
            new FileSystemSourceUnits(new ProcessCommandExecutor()),
            GateFrontends.create(new AntlrLanguageFrontend()),
            projectRoot -> new FileAnalysisCache(projectRoot.resolve(".ward/cache/analysis")),
            AnalysisCacheFixtures.codec(),
            "test",
            new ProjectDependencyAdapters(
                List.of(
                    new NodeDependencyAdapters(),
                    new PythonDependencyAdapters(),
                    new SwiftDependencyAdapters(),
                    new JvmDependencyAdapters())),
            (unused, classpath) -> Map.of());
    return AnalysisGates.create(check, ports)
        .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
  }

  /** Copies the files under {@code source} that {@code keep} accepts into {@code root}. */
  static void copy(final Path source, final Path root, final Predicate<Path> keep) {
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(Files::isRegularFile).filter(keep).toList()) {
        final Path target = root.resolve(source.relativize(file).toString());
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
      }
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
