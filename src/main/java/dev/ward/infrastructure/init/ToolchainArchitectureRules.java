package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import javax.inject.Inject;

/**
 * Generates a project's architecture rules with the generator of its toolchain: an ArchUnit test
 * for the JVM and a dependency-cruiser config for Node. Other toolchains use the JVM generator.
 */
public final class ToolchainArchitectureRules implements ArchitectureTestGeneration {
  private final ArchitectureTestGenerator jvm;
  private final DependencyCruiserConfigGenerator node;

  /** Creates the router that Dagger injects. */
  @Inject
  public ToolchainArchitectureRules(
      final ArchitectureTestGenerator jvm, final DependencyCruiserConfigGenerator node) {
    this.jvm = jvm;
    this.node = node;
  }

  @Override
  public List<String> existingArchUnitTests(final Project project) {
    return generator(project.toolchain()).existingArchUnitTests(project);
  }

  @Override
  public ArchitectureTestChange apply(final Project project, final String architecture) {
    return generator(project.toolchain()).apply(project, architecture);
  }

  @Override
  public ArchitectureTestChange apply(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    return generator(project.toolchain()).apply(project, architecture, extras);
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    return generator(project.toolchain()).plan(project, architecture, extras);
  }

  /**
   * Restores a config that a rewrite replaced. Any other change is undone by the JVM generator,
   * which deletes a file the change created.
   */
  @Override
  public void rollback(final ArchitectureTestChange change) {
    if (change != null && change.previous() != null) {
      node.rollback(change);
    } else {
      jvm.rollback(change);
    }
  }

  /** Returns the generator for {@code toolchain}. */
  ArchitectureTestGeneration generator(final Toolchain toolchain) {
    return switch (toolchain) {
      case NODE -> node;
      case JVM -> jvm;
      default -> jvm;
    };
  }
}
