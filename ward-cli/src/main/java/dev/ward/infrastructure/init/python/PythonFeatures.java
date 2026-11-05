package dev.ward.infrastructure.init.python;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The features of a Python project that the independence rules keep apart: the Django apps, the
 * Flask blueprints or the root's feature packages, as import-linter modules and as directories.
 */
final class PythonFeatures {
  /** The fewest features that an independence rule is written for. */
  static final int MIN_FEATURES = 2;

  private PythonFeatures() {}

  /**
   * Returns the module names of the features: the Django apps, the Flask blueprints or the root's
   * feature packages, excluding the layers of the style.
   */
  static List<String> modules(
      final Project project, final List<String> roots, final Set<String> layers) {
    return switch (project.framework()) {
      case DJANGO -> PythonPackages.djangoApps(project);
      case FLASK ->
          FlaskPackages.blueprints(project, roots.getFirst()).stream()
              .map(feature -> roots.getFirst() + "." + feature)
              .toList();
      default ->
          PythonPackages.features(project, roots.getFirst(), layers).stream()
              .map(feature -> roots.getFirst() + "." + feature)
              .toList();
    };
  }

  /**
   * Returns the directories of the features that the independence contract lists for {@code
   * architecture}, for the ward engine's independence table. Empty when there are fewer than {@code
   * MIN_FEATURES}.
   */
  static List<String> directories(final Project project, final String architecture) {
    final List<String> roots = PythonPackages.roots(project);
    final Set<String> layers =
        architecture == null
            ? Set.of()
            : names(ArchitectureLayers.forStyle(architecture, project.framework()));
    final List<String> features = modules(project, roots, layers);
    if (features.size() < MIN_FEATURES) {
      return List.of();
    }
    if (project.framework() == Framework.DJANGO) {
      return features;
    }
    final String base = directory(project, roots.getFirst());
    final int prefix = roots.getFirst().length() + 1;
    return features.stream().map(module -> base + "/" + module.substring(prefix)).toList();
  }

  /** The project-relative directory of the root package {@code rootPackage}. */
  static String directory(final Project project, final String rootPackage) {
    return project
        .root()
        .relativize(PythonPackages.packageDirectory(project.root(), rootPackage))
        .toString()
        .replace('\\', '/');
  }

  /** The layer names of a style: the names of its source and forbidden layers. */
  static Set<String> names(final LayerBoundary layers) {
    return Set.copyOf(Stream.concat(layers.from().stream(), layers.forbidden().stream()).toList());
  }
}
