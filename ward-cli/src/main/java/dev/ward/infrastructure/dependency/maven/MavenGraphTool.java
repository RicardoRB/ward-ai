package dev.ward.infrastructure.dependency.maven;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.ResolvedGraphTool;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.ToolRuns;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves a Maven reactor by running a pinned {@code maven-dependency-plugin} with the verbose
 * tree in TGF, through the project's wrapper when it has one. The plugin version is ours, not the
 * project's, so its {@code pluginManagement} does not change the result.
 */
public final class MavenGraphTool implements ResolvedGraphTool {
  /** The pinned plugin version: the newest release that is at least two weeks old. */
  public static final String PLUGIN_VERSION = "3.11.0";

  private static final Logger LOGGER = Logger.getLogger(MavenGraphTool.class.getName());
  private static final String PLUGIN_GOAL =
      "org.apache.maven.plugins:maven-dependency-plugin:" + PLUGIN_VERSION + ":tree";

  private final CommandExecutor executor;
  private final boolean offline;

  /**
   * Creates the tool.
   *
   * @param executor runs the build tool
   * @param offline whether to resolve from the local repository only
   */
  public MavenGraphTool(final CommandExecutor executor, final boolean offline) {
    this.executor = executor;
    this.offline = offline;
  }

  @Override
  public DependencyInventory resolve(final Project project) {
    final MavenModules modules = MavenModules.read(project.root());
    final List<String> arguments = new ArrayList<>();
    arguments.add(project.buildExecutable());
    arguments.add("--batch-mode");
    arguments.add("--no-transfer-progress");
    if (offline) {
      arguments.add("--offline");
    }
    arguments.add(PLUGIN_GOAL);
    arguments.add("-DoutputType=tgf");
    arguments.add("-Dverbose");
    final String output = ToolRuns.run(executor, project, arguments);
    final DependencyInventory inventory =
        MavenTgf.read(project.root().toString(), output, modules.paths());
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-resolved tool=maven modules="
                + inventory.modules().size()
                + " entries="
                + inventory.entryCount());
    return inventory;
  }
}
