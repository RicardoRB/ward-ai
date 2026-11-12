package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.ResolvedGraphTool;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.dependency.ToolRuns;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Resolves a Gradle build by running an init script that Ward writes under {@code
 * .ward/cache/dependencies/}. The script is a resource: it lists the resolved graph of every
 * project and leaves the project's files alone.
 */
public final class GradleGraphTool implements ResolvedGraphTool {
  /** The resource path of the init script, which the native image must include. */
  public static final String SCRIPT_RESOURCE =
      "/dev/ward/infrastructure/dependency/gradle/ward-dependencies.init.gradle";

  private static final Logger LOGGER = Logger.getLogger(GradleGraphTool.class.getName());
  private static final Path WORK = Path.of(".ward", "cache", "dependencies", "gradle-run");
  private static final String SCRIPT_NAME = "ward-dependencies.init.gradle";

  private final CommandExecutor executor;
  private final boolean offline;

  /**
   * Creates the tool.
   *
   * @param executor runs the build tool
   * @param offline whether Gradle resolves from its cache only
   */
  public GradleGraphTool(final CommandExecutor executor, final boolean offline) {
    this.executor = executor;
    this.offline = offline;
  }

  @Override
  public DependencyInventory resolve(final Project project) {
    final Path work = project.root().resolve(WORK);
    final Path output = work.resolve("out");
    try {
      delete(work);
      Files.createDirectories(output);
      final Path script = work.resolve(SCRIPT_NAME);
      Files.write(script, scriptBytes());
      final String run = ToolRuns.run(executor, project, arguments(project, script, output));
      final DependencyInventory inventory = read(project, output);
      LOGGER.log(
          Level.FINE,
          () ->
              "event=dependency-resolved tool=gradle modules="
                  + inventory.modules().size()
                  + " entries="
                  + inventory.entryCount()
                  + " output="
                  + run.length());
      return inventory;
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          ".ward/cache/dependencies",
          "the Gradle listing cannot be written or read",
          "check the permissions of the project folder",
          e);
    } finally {
      delete(work);
    }
  }

  private List<String> arguments(final Project project, final Path script, final Path output) {
    final List<String> arguments = new ArrayList<>();
    arguments.add(project.buildExecutable());
    arguments.add("-I");
    arguments.add(script.toString());
    arguments.add("wardDependencies");
    arguments.add("--console=plain");
    if (offline) {
      arguments.add("--offline");
    }
    arguments.add("-Dorg.gradle.configuration-cache=false");
    arguments.add("-Dward.dependencies.dir=" + output);
    return arguments;
  }

  private static DependencyInventory read(final Project project, final Path output)
      throws IOException {
    final List<String> texts = new ArrayList<>();
    try (Stream<Path> files = Files.list(output)) {
      for (final Path file : files.filter(f -> f.toString().endsWith(".tsv")).sorted().toList()) {
        texts.add(Files.readString(file, StandardCharsets.UTF_8));
      }
    }
    if (texts.isEmpty()) {
      throw new DependencyInventoryException(
          project.root().toString(),
          "the Gradle dependency listing",
          "Gradle wrote no result",
          "run the Gradle build to see why");
    }
    return GradleTsv.read(
        project.root().toString(),
        texts,
        path -> {
          final Path dir = path.isEmpty() ? project.root() : project.root().resolve(path);
          final String script = GradleModules.buildScript(dir);
          return LockFiles.join(path, script == null ? "settings.gradle" : script);
        });
  }

  private static byte[] scriptBytes() throws IOException {
    try (InputStream stream = GradleGraphTool.class.getResourceAsStream(SCRIPT_RESOURCE)) {
      if (stream == null) {
        throw new IOException("the init script resource is missing");
      }
      return stream.readAllBytes();
    }
  }

  private static void delete(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(directory)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "event=dependency-cleanup result=failed");
    }
  }
}
