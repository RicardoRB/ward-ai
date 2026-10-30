package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Reads a Flask project's blueprints and app factory from its package sources. */
final class FlaskPackages {
  private FlaskPackages() {}

  /**
   * Returns the Flask blueprint packages: the root's subpackages that create a {@code Blueprint},
   * the features that strict keeps independent.
   */
  static List<String> blueprints(final Project project, final String rootPackage) {
    final Path base = PythonPackages.packageDirectory(project.root(), rootPackage);
    return PythonPackages.packages(base).stream()
        .filter(name -> definesBlueprint(base.resolve(name)))
        .sorted()
        .toList();
  }

  /** Returns whether the project's root package defines {@code create_app}, Flask's app factory. */
  static boolean hasAppFactory(final Project project) {
    return PythonPackages.root(project)
        .map(name -> PythonPackages.packageDirectory(project.root(), name))
        .filter(dir -> Files.isRegularFile(dir.resolve("__init__.py")))
        .map(dir -> pythonText(dir.resolve("__init__.py")) + pythonText(dir.resolve("app.py")))
        .map(text -> text.contains("def create_app("))
        .orElse(false);
  }

  private static boolean definesBlueprint(final Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(file -> file.toString().endsWith(".py"))
          .anyMatch(file -> pythonText(file).contains("Blueprint("));
    } catch (IOException e) {
      throw new WardException("Could not list " + dir + ": " + e.getMessage(), e);
    }
  }

  /** Returns the text of a Python file, or empty when it is missing. */
  private static String pythonText(final Path file) {
    return Files.isRegularFile(file) ? read(file) : "";
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
