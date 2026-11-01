package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.node.DependencyCruiserConfigGenerator;
import dev.ward.infrastructure.init.python.ImportLinterConfigGenerator;
import dev.ward.infrastructure.init.swift.HarmonizeRulesGenerator;
import java.util.List;
import javax.inject.Inject;

/**
 * Generates a project's architecture rules with the generator of its toolchain: an ArchUnit test
 * for the JVM, a dependency-cruiser config for Node, an import-linter config for Python and
 * Harmonize rules for Swift.
 */
public final class ToolchainArchitectureRules implements ArchitectureTestGeneration {
  private final ArchitectureTestGenerator jvm;
  private final DependencyCruiserConfigGenerator node;
  private final ImportLinterConfigGenerator python;
  private final HarmonizeRulesGenerator swift;

  /** Creates the router that Dagger injects. */
  @Inject
  public ToolchainArchitectureRules(
      final ArchitectureTestGenerator jvm,
      final DependencyCruiserConfigGenerator node,
      final ImportLinterConfigGenerator python,
      final HarmonizeRulesGenerator swift) {
    this.jvm = jvm;
    this.node = node;
    this.python = python;
    this.swift = swift;
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
   * Restores a config that a rewrite replaced. A Swift rules file is undone by the Harmonize
   * generator, which removes its package. Any other change is undone by the JVM generator, which
   * deletes a file the change created.
   */
  @Override
  public void rollback(final ArchitectureTestChange change) {
    if (change != null && HarmonizeRulesGenerator.owns(change.file())) {
      swift.rollback(change);
    } else if (change != null && change.previous() != null) {
      node.rollback(change);
    } else {
      jvm.rollback(change);
    }
  }

  /** Returns the generator for {@code toolchain}. */
  ArchitectureTestGeneration generator(final Toolchain toolchain) {
    return switch (toolchain) {
      case NODE -> node;
      case PYTHON -> python;
      case SWIFT -> swift;
      case JVM -> jvm;
      default -> jvm;
    };
  }
}
