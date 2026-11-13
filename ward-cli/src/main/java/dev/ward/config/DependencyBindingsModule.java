package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.GraphCache;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.BuildTool;
import dev.ward.infrastructure.dependency.FileGraphCache;
import dev.ward.infrastructure.dependency.gradle.GradleDependencySource;
import dev.ward.infrastructure.dependency.maven.MavenDependencySource;
import dev.ward.infrastructure.dependency.node.NodeDependencySource;
import dev.ward.infrastructure.dependency.python.PythonDependencySource;
import dev.ward.infrastructure.dependency.swift.SwiftDependencySource;
import java.util.EnumMap;
import java.util.Map;

/**
 * Binds the dependency inventory: one source per build tool, with an explicit constructor graph.
 */
@Module
abstract class DependencyBindingsModule {
  @Binds
  abstract GraphCache graphCache(FileGraphCache implementation);

  @Provides
  static Map<BuildTool, DependencySource> dependencySources(
      final CommandExecutor executor, final GraphCache cache, final JsonCodec codec) {
    final DependencySource node = new NodeDependencySource(codec);
    final DependencySource python = new PythonDependencySource();
    final DependencySource swift = new SwiftDependencySource(codec);
    final Map<BuildTool, DependencySource> sources = new EnumMap<>(BuildTool.class);
    sources.put(BuildTool.MAVEN, new MavenDependencySource(executor, cache, codec, false));
    sources.put(BuildTool.GRADLE, new GradleDependencySource(executor, cache, codec, false));
    sources.put(BuildTool.NPM, node);
    sources.put(BuildTool.PNPM, node);
    sources.put(BuildTool.YARN, node);
    sources.put(BuildTool.BUN, node);
    sources.put(BuildTool.UV, python);
    sources.put(BuildTool.POETRY, python);
    sources.put(BuildTool.PIP, python);
    sources.put(BuildTool.SWIFTPM, swift);
    sources.put(BuildTool.XCODE, swift);
    return sources;
  }
}
