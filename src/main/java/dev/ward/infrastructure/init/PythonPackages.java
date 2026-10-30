package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.PyprojectDependencies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds a Python project's root package and its feature packages, the packages import-linter
 * contracts name. A package is a directory with an {@code __init__.py}.
 */
final class PythonPackages {
  private static final String SRC = "src";
  private static final String INIT = "__init__.py";
  private static final Set<String> NOT_ROOT = Set.of("tests", "test");
  private static final int SINGLE = 1;

  private PythonPackages() {}

  /**
   * Returns the project's root package: the {@code [project] name} when that package exists, else
   * the only package under {@code src/}, else the only top-level package other than the tests.
   */
  static Optional<String> root(final Project project) {
    final Path root = project.root();
    final List<String> src = packages(root.resolve(SRC));
    final List<String> top =
        packages(root).stream().filter(name -> !NOT_ROOT.contains(name)).toList();
    final Optional<String> named = projectName(root);
    if (named.isPresent() && (src.contains(named.get()) || top.contains(named.get()))) {
      return named;
    }
    if (src.size() == SINGLE) {
      return Optional.of(src.getFirst());
    }
    return src.isEmpty() && top.size() == SINGLE ? Optional.of(top.getFirst()) : Optional.empty();
  }

  /**
   * Returns the root packages import-linter names. A Django project has several: each top-level
   * package, its settings package and its apps. Any other project has the one root package.
   *
   * @throws WardException if a non-Django project has no root package
   */
  static List<String> roots(final Project project) {
    if (project.framework() == Framework.DJANGO) {
      return packages(project.root()).stream().filter(name -> !NOT_ROOT.contains(name)).toList();
    }
    return root(project)
        .map(List::of)
        .orElseThrow(
            () ->
                new WardException(
                    "import-linter needs the project's root package, and init finds none: keep one"
                        + " package under src/ or set [project] name to it."));
  }

  /** Returns the Django apps: the top-level packages that define an {@code apps.py}. */
  static List<String> djangoApps(final Project project) {
    return roots(project).stream()
        .filter(name -> Files.isRegularFile(project.root().resolve(name).resolve("apps.py")))
        .sorted()
        .toList();
  }

  /**
   * Returns the packages directly under {@code rootPackage} that are not layers of the style: each
   * is a feature that strict keeps independent. Sorted, so the config is stable.
   */
  static List<String> features(
      final Project project, final String rootPackage, final Set<String> layers) {
    final Path base = packageDirectory(project.root(), rootPackage);
    return packages(base).stream()
        .filter(name -> !layers.contains(name) && !NOT_ROOT.contains(name))
        .sorted()
        .toList();
  }

  /** Returns the directory of {@code name}, under {@code src/} when it is there. */
  static Path packageDirectory(final Path root, final String name) {
    final Path inSrc = root.resolve(SRC).resolve(name);
    return Files.isDirectory(inSrc) ? inSrc : root.resolve(name);
  }

  /** Returns the names of the packages directly inside {@code dir}, sorted. */
  static List<String> packages(final Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          .filter(entry -> Files.isRegularFile(entry.resolve(INIT)))
          .map(entry -> dir.relativize(entry).toString())
          .sorted(Comparator.naturalOrder())
          .toList();
    } catch (IOException e) {
      throw new WardException("Could not list " + dir + ": " + e.getMessage(), e);
    }
  }

  /** Returns the project name as a package name: lowercase, with dashes and dots as underscores. */
  private static Optional<String> projectName(final Path root) {
    final Path pyproject = root.resolve("pyproject.toml");
    if (!Files.isRegularFile(pyproject)) {
      return Optional.empty();
    }
    try {
      final String name =
          PyprojectDependencies.parse(Files.readString(pyproject)).getString("project.name");
      return name == null
          ? Optional.empty()
          : Optional.of(name.toLowerCase(java.util.Locale.ROOT).replaceAll("[-.]", "_"));
    } catch (IOException e) {
      throw new WardException("Could not read " + pyproject + ": " + e.getMessage(), e);
    }
  }
}
