package dev.ward.infrastructure.dependency.maven;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.GraphCache;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.ResolvingDependencySource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads the inventory of a Maven project from the build tool, through the graph cache. The cache is
 * keyed by every POM of the reactor and the files of {@code .mvn/}.
 */
public final class MavenDependencySource implements DependencySource {
  private final ResolvingDependencySource delegate;

  /**
   * Creates the source.
   *
   * @param executor runs the build tool
   * @param cache stores resolved graphs
   * @param codec writes the stored document
   * @param offline whether Maven resolves from the local repository only
   */
  public MavenDependencySource(
      final CommandExecutor executor,
      final GraphCache cache,
      final JsonCodec codec,
      final boolean offline) {
    this.delegate =
        new ResolvingDependencySource(
            new MavenGraphTool(executor, offline),
            MavenDependencySource::buildFiles,
            "maven-dependency-plugin:" + MavenGraphTool.PLUGIN_VERSION + ":tree:tgf:verbose",
            cache,
            codec);
  }

  @Override
  public DependencyInventory read(final Project project) {
    return delegate.read(project);
  }

  /** Returns every POM of the reactor and the regular files below {@code .mvn}. */
  private static List<Path> buildFiles(final Project project) {
    final List<Path> files = new ArrayList<>(MavenModules.read(project.root()).poms());
    final Path mvn = project.root().resolve(".mvn");
    if (Files.isDirectory(mvn)) {
      try (Stream<Path> walk = Files.walk(mvn)) {
        walk.filter(Files::isRegularFile).forEach(files::add);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return files;
  }
}
