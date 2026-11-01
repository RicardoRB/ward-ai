package dev.ward.infrastructure.init.python;

import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.project.PyprojectDependencies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Reads and extends {@code requirements-ward.txt} and the requirements files a project keeps. */
final class PythonRequirementsFile {
  private static final String WARD_MARKER = "# managed-by: ward\n";
  private static final String REQUIREMENT_FILES = ".*requirements[^/]*\\.txt";

  private PythonRequirementsFile() {}

  /** Returns the names that {@code requirements*.txt} files in {@code root} declare. */
  static Set<String> declaredNames(final Path root) {
    final Set<String> names = new HashSet<>();
    try (Stream<Path> files = Files.list(root)) {
      files
          .filter(file -> file.toString().matches(REQUIREMENT_FILES))
          .forEach(
              file ->
                  read(file)
                      .lines()
                      .forEach(line -> PyprojectDependencies.nameOf(line).ifPresent(names::add)));
    } catch (IOException e) {
      throw new WardException("Could not list " + root + ": " + e.getMessage(), e);
    }
    return names;
  }

  static String withRequirements(final String existing, final List<String> requirements) {
    final StringBuilder text = new StringBuilder(existing == null ? WARD_MARKER : existing);
    if (existing != null && !existing.isEmpty() && !existing.endsWith("\n")) {
      text.append('\n');
    }
    requirements.forEach(requirement -> text.append(requirement).append('\n'));
    return text.toString();
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
