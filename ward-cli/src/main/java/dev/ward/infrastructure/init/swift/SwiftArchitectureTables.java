package dev.ward.infrastructure.init.swift;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.swift.SwiftLayouts;
import java.util.List;
import javax.inject.Inject;

/** The Swift architecture tables: the source directories of the project's targets. */
public final class SwiftArchitectureTables implements ArchitectureTables {

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public SwiftArchitectureTables() {}

  /**
   * The source directories of the project's Swift targets, from its manifest and Xcode projects.
   */
  @Override
  public List<String> cycleRoots(final Project project) {
    return SwiftLayouts.read(project.root()).targets().stream()
        .flatMap(target -> target.directories().stream())
        .distinct()
        .sorted()
        .toList();
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return List.of();
  }
}
