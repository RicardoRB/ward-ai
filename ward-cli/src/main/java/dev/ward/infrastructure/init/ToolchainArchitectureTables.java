package dev.ward.infrastructure.init;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.maven.MavenCodeRoot;
import dev.ward.infrastructure.init.node.NodeArchitectureTables;
import dev.ward.infrastructure.init.python.PythonArchitectureTables;
import dev.ward.infrastructure.init.swift.SwiftArchitectureTables;
import java.util.List;
import javax.inject.Inject;

/** Answers the architecture tables of a project with the tables of its toolchain. */
public final class ToolchainArchitectureTables implements ArchitectureTables {
  private final JavaArchitectureTables jvm;
  private final NodeArchitectureTables node;
  private final PythonArchitectureTables python;
  private final SwiftArchitectureTables swift;

  /** Creates the router that Dagger injects. */
  @Inject
  public ToolchainArchitectureTables(
      final JavaArchitectureTables jvm,
      final NodeArchitectureTables node,
      final PythonArchitectureTables python,
      final SwiftArchitectureTables swift) {
    this.jvm = jvm;
    this.node = node;
    this.python = python;
    this.swift = swift;
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
    return switch (toolchain) {
      case NODE -> node;
      case PYTHON -> python;
      case SWIFT -> swift;
      case JVM -> jvm;
      default -> jvm;
    };
  }
}
