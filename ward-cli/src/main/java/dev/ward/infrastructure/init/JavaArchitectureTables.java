package dev.ward.infrastructure.init;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import java.util.List;
import javax.inject.Inject;

/** The Java architecture tables: the cycle check covers the base package of the main sources. */
public final class JavaArchitectureTables implements ArchitectureTables {

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public JavaArchitectureTables() {}

  @Override
  public List<String> cycleRoots(final Project project) {
    return List.of("src/main/java/" + JavaBasePackage.of(project.root()).replace('.', '/'));
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return List.of();
  }
}
