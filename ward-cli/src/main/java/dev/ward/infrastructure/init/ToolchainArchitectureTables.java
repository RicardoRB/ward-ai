package dev.ward.infrastructure.init;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.maven.MavenCodeRoot;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Answers the architecture tables of a project with the tables of its toolchain. */
public final class ToolchainArchitectureTables implements ArchitectureTables {
  private final JavaArchitectureTables jvm;
  private final Map<Toolchain, ArchitectureTables> byToolchain;

  /** Creates the router that Dagger injects: Java's tables, and those of every other toolchain. */
  @Inject
  public ToolchainArchitectureTables(
      final JavaArchitectureTables jvm,
      final Map<Toolchain,
      ArchitectureTables> byToolchain
  ) {
    this.jvm = jvm;
    this.byToolchain = Map.copyOf(byToolchain);
  }

  @Override
  public List<String> cycleRoots(final Project project) {
    return tables(project.toolchain()).cycleRoots(codeProject(project));
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return tables(project.toolchain()).independenceModules(codeProject(project), style);
  }

  /** The project whose sources the tables describe: a Maven aggregator's owning module, if any. */
  private static Project codeProject(final Project project) {
    return MavenCodeRoot.of(project);
  }

  /** Returns the tables of {@code toolchain}. */
  private ArchitectureTables tables(final Toolchain toolchain) {
    return byToolchain.getOrDefault(toolchain, jvm);
  }
}
