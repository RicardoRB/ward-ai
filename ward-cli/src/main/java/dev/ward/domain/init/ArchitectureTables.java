package dev.ward.domain.init;

import dev.ward.domain.project.Project;
import java.util.List;

/**
 * The project-relative roots and module directories that the {@code ward} architecture tables list.
 * Each toolchain answers for its own layout.
 */
public interface ArchitectureTables {

  /**
   * Returns the project-relative source roots whose slices the cycle check covers; none when the
   * toolchain gives no roots.
   */
  List<String> cycleRoots(Project project);

  /**
   * Returns the project-relative module directories that the independence table lists for {@code
   * style}; none when the toolchain has no features to keep apart.
   */
  List<String> independenceModules(Project project, String style);
}
