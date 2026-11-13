package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.GraphCache;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.ResolvingDependencySource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reads the inventory of a Gradle project: from its lockfiles when the build locks every
 * configuration and the build tool is not needed, otherwise from the build tool through the graph
 * cache, keyed by the build scripts, settings, properties, catalogs and wrapper properties.
 */
public final class GradleDependencySource implements DependencySource {
  private final ResolvingDependencySource tool;

  /**
   * Creates the source.
   *
   * @param executor runs the build tool
   * @param cache stores resolved graphs
   * @param codec writes the stored document
   * @param offline whether Gradle resolves from its cache only
   */
  public GradleDependencySource(
      final CommandExecutor executor,
      final GraphCache cache,
      final JsonCodec codec,
      final boolean offline) {
    this.tool =
        new ResolvingDependencySource(
            new GradleGraphTool(executor, offline),
            project -> GradleModules.buildFiles(project.root()),
            "ward-dependencies.init.gradle:" + scriptHash(),
            cache,
            codec);
  }

  @Override
  public DependencyInventory read(final Project project) {
    return GradleLockSource.read(project).orElseGet(() -> tool.read(project));
  }

  private static String scriptHash() {
    try (InputStream stream =
        GradleGraphTool.class.getResourceAsStream(GradleGraphTool.SCRIPT_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("the init script resource is missing");
      }
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
    }
  }
}
