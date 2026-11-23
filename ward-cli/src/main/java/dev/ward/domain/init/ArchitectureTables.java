package dev.ward.domain.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.project.Project;
import java.util.List;

/**
 * The project-relative roots and module directories that the {@code ward} architecture tables list.
 * Each toolchain answers for its own layout.
 */
public interface ArchitectureTables {
  /**
   * Returns the layer boundaries init writes for {@code style}; none when the toolchain has no
   * layout that a layer rule can match, so init writes no rule that can never fire. The default is
   * the boundary of the style, for the toolchains whose projects use those folder names.
   */
  default List<LayerBoundary> layers(final Project project, final String style) {
    return List.of(ArchitectureLayers.forStyle(style, project.framework()));
  }

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
