package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.util.List;
import javax.inject.Inject;

/** The Node architecture tables: the cycle check covers {@code src}, or the project root. */
public final class NodeArchitectureTables implements ArchitectureTables {

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public NodeArchitectureTables() {}

  @Override
  public List<String> cycleRoots(final Project project) {
    return Files.isDirectory(project.root().resolve("src")) ? List.of("src") : List.of(".");
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return List.of();
  }
}
