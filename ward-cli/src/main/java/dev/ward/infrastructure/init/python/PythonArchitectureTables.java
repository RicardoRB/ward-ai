package dev.ward.infrastructure.init.python;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import java.util.List;
import javax.inject.Inject;

/** The Python architecture tables: the packages of each root, and the features of the style. */
public final class PythonArchitectureTables implements ArchitectureTables {

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public PythonArchitectureTables() {}

  @Override
  public List<String> cycleRoots(final Project project) {
    return PythonPackages.roots(project).stream()
        .map(root -> PythonFeatures.directory(project, root))
        .toList();
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return PythonFeatures.directories(project, style);
  }
}
