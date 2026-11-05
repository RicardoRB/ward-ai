package dev.ward.infrastructure.init.python;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Renders the import-linter config that enforces an architecture style, with the ownership marker.
 */
final class ImportLinterContracts {
  private static final String MARKER = "# managed-by: ward\n";
  private static final int MIN_FEATURES = 2;

  private ImportLinterContracts() {}

  /** Returns the config for {@code architecture}, with the independence contract when strict. */
  static String render(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final List<String> roots = PythonPackages.roots(project);
    final LayerBoundary layers =
        architecture == null
            ? null
            : ArchitectureLayers.forStyle(architecture, project.framework());
    final Set<String> names = layers == null ? Set.of() : names(layers);
    return MARKER
        + "[importlinter]\n"
        + rootLines(project, roots)
        + (layers == null ? "" : layersContract(project, roots, architecture, layers))
        + (extras.cycles() ? independence(project, roots, names) : "");
  }

  /** The layers contract: the style's sources may not import its forbidden layers. */
  private static String layersContract(
      final Project project,
      final List<String> roots,
      final String architecture,
      final LayerBoundary layers) {
    return "\n[importlinter:contract:layers]\nname = "
        + architecture
        + " layers\ntype = forbidden\nsource_modules =\n"
        + modules(project, roots, layers.from())
        + "forbidden_modules =\n"
        + modules(project, roots, layers.forbidden());
  }

  /**
   * Names one root package, or every root of a Django project, which import-linter reads as a list.
   */
  private static String rootLines(final Project project, final List<String> roots) {
    if (project.framework() != Framework.DJANGO) {
      return "root_package = " + roots.getFirst() + "\n";
    }
    return "root_packages =\n"
        + roots.stream().map(root -> "    " + root + "\n").reduce("", String::concat);
  }

  /**
   * Returns the independence contract between the features, or empty when there are fewer than
   * {@code MIN_FEATURES}.
   */
  private static String independence(
      final Project project, final List<String> roots, final Set<String> layers) {
    final List<String> features = featureModules(project, roots, layers);
    if (features.size() < MIN_FEATURES) {
      return "";
    }
    return "\n[importlinter:contract:features]\nname = Features are independent\n"
        + "type = independence\nmodules =\n"
        + features.stream().map(feature -> "    " + feature + "\n").reduce("", String::concat);
  }

  /**
   * Returns the module names of the features: the Django apps, the Flask blueprints or the root's
   * feature packages, excluding the layers of the style.
   */
  private static List<String> featureModules(
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
   * architecture}, for the ward engine's independence table. Empty when there are fewer than
   * {@code MIN_FEATURES}.
   */
  static List<String> independenceDirectories(final Project project, final String architecture) {
    final List<String> roots = PythonPackages.roots(project);
    final Set<String> layers =
        architecture == null
            ? Set.of()
            : names(ArchitectureLayers.forStyle(architecture, project.framework()));
    final List<String> features = featureModules(project, roots, layers);
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
  private static Set<String> names(final LayerBoundary layers) {
    return Set.copyOf(concat(layers.from(), layers.forbidden()));
  }

  /** Returns each layer of each root as a module expression, at any depth below the root. */
  private static String modules(
      final Project project, final List<String> roots, final List<String> layers) {
    return roots.stream().map(root -> modules(project, root, layers)).reduce("", String::concat);
  }

  /**
   * Returns each layer of one root as a module expression at any depth below it. A layer that is a
   * package directly under the root is also named exactly, since import-linter's {@code **} needs
   * at least one package level; an exact name must exist, so it is listed only when the package
   * does.
   */
  private static String modules(
      final Project project, final String rootPackage, final List<String> layers) {
    final Path base = PythonPackages.packageDirectory(project.root(), rootPackage);
    return layers.stream()
        .map(
            layer -> exact(base, rootPackage, layer) + "    " + rootPackage + ".**." + layer + "\n")
        .reduce("", String::concat);
  }

  private static String exact(final Path base, final String rootPackage, final String layer) {
    return Files.isRegularFile(base.resolve(layer).resolve("__init__.py"))
        ? "    " + rootPackage + "." + layer + "\n"
        : "";
  }

  private static List<String> concat(final List<String> first, final List<String> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
  }
}
