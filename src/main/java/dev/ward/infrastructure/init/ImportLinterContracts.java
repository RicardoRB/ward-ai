package dev.ward.infrastructure.init;

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
  private static final List<String> FASTAPI_CONTROLLERS = List.of("routers", "api", "endpoints");
  private static final List<String> FASTAPI_REPOSITORIES = List.of("repositories", "crud");
  private static final int MIN_FEATURES = 2;

  /** The layers of a style: sources may not import the forbidden layers. */
  private record Layers(List<String> sources, List<String> forbidden) {}

  private ImportLinterContracts() {}

  /** Returns the config for {@code architecture}, with the independence contract when strict. */
  static String render(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final List<String> roots = PythonPackages.roots(project);
    final Layers layers = layers(architecture, project.framework());
    final Set<String> names = Set.copyOf(concat(layers.sources(), layers.forbidden()));
    return MARKER
        + "[importlinter]\n"
        + rootLines(project, roots)
        + "\n[importlinter:contract:layers]\nname = "
        + architecture
        + " layers\ntype = forbidden\nsource_modules =\n"
        + modules(project, roots, layers.sources())
        + "forbidden_modules =\n"
        + modules(project, roots, layers.forbidden())
        + (extras.cycles() ? independence(project, roots, names) : "");
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
   * Returns the independence contract between the features: the Django apps, or the root's feature
   * packages. Empty when there are fewer than {@code MIN_FEATURES}.
   */
  private static String independence(
      final Project project, final List<String> roots, final Set<String> layers) {
    final List<String> features =
        switch (project.framework()) {
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
    if (features.size() < MIN_FEATURES) {
      return "";
    }
    return "\n[importlinter:contract:features]\nname = Features are independent\n"
        + "type = independence\nmodules =\n"
        + features.stream().map(feature -> "    " + feature + "\n").reduce("", String::concat);
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

  /** Returns a style's layers, with FastAPI's router and repository package names when FastAPI. */
  private static Layers layers(final String architecture, final Framework framework) {
    final Layers base = baseLayers(architecture);
    if (framework != Framework.FASTAPI) {
      return base;
    }
    return new Layers(
        concat(base.sources(), FASTAPI_CONTROLLERS),
        concat(base.forbidden(), FASTAPI_REPOSITORIES));
  }

  private static Layers baseLayers(final String architecture) {
    return switch (architecture) {
      case "hexagonal" ->
          new Layers(List.of("domain", "application"), List.of("adapter", "infrastructure"));
      case "clean" ->
          new Layers(List.of("domain", "core"), List.of("controller", "adapter", "infrastructure"));
      default -> new Layers(List.of("controller"), List.of("repository"));
    };
  }

  private static List<String> concat(final List<String> first, final List<String> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
  }
}
