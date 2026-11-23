package dev.ward.infrastructure.init.dotnet;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import javax.inject.Inject;
import java.util.List;

/** The C# architecture tables: none yet, so strict writes no layer or independence rules. */
public final class DotnetArchitectureTables implements ArchitectureTables {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public DotnetArchitectureTables() {}

  @Override
  public List<LayerBoundary> layers(final Project project, final String style) {
    return List.of();
  }

  @Override
  public List<String> cycleRoots(final Project project) {
    return List.of();
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return List.of();
  }
}
