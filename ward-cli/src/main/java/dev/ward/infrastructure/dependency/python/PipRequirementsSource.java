package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.project.python.PythonProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the inventory of a pip project from its pinned {@code requirements*.txt} files. The files
 * record no edges, so a requirement is transitive only when its pip-compile {@code # via}
 * annotation names another package.
 */
final class PipRequirementsSource {
  private PipRequirementsSource() {}

  static DependencyInventory read(final Project project) {
    final Path root = project.root();
    final List<Path> files = requirementsFiles(root);
    final ModuleBuilder builder = new ModuleBuilder("requirements.txt", false);
    if (files.isEmpty()) {
      final PythonManifest manifest = PythonManifest.read(root, root, project);
      if (manifest.declaresPackages()) {
        throw new DependencyInventoryException(
            root.toString(),
            "requirements.txt",
            "the project declares dependencies and has no pinned requirements file",
            "run `pip freeze > requirements.txt` or `pip-compile`");
      }
      return new DependencyInventory(List.of(new ModuleDependencies("", List.of())));
    }
    final Map<PackageId, String> sources = new HashMap<>();
    files.forEach(file -> readFile(root, file, builder, sources));
    return new DependencyInventory(List.of(withSources(builder.build(""), sources)));
  }

  private static void readFile(
      final Path root,
      final Path file,
      final ModuleBuilder builder,
      final Map<PackageId, String> sources) {
    final DependencyScope scope = scopeOf(LockFiles.fileName(file));
    try {
      for (final PinnedRequirements.Pin pin : PinnedRequirements.read(root, file)) {
        final PackageId id = PythonLock.pypi(pin.name(), pin.version());
        builder.add(id, true).scope(id, scope);
        if (!pin.transitive()) {
          builder.direct(id, scope, pin.file());
        }
        sources.merge(id, pin.file(), (a, b) -> a.compareTo(b) <= 0 ? a : b);
      }
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          root.toString(),
          LockFiles.relative(root, file),
          e.getMessage(),
          "pin every requirement with `==`, for example with `pip-compile`",
          e);
    }
  }

  /** Gives each entry the file its pin was read from instead of the first file read. */
  private static ModuleDependencies withSources(
      final ModuleDependencies module, final Map<PackageId, String> sources) {
    return new ModuleDependencies(
        module.modulePath(),
        module.dependencies().stream()
            .map(
                entry ->
                    new Dependency(
                        entry.id(),
                        entry.relation(),
                        entry.scope(),
                        entry.released(),
                        sources.get(entry.id()),
                        entry.declaredIn(),
                        entry.requiredBy(),
                        entry.requested()))
            .toList());
  }

  private static DependencyScope scopeOf(final String file) {
    if (file.contains("dev")) {
      return DependencyScope.DEVELOPMENT;
    }
    return file.contains("test") ? DependencyScope.TEST : DependencyScope.RUNTIME;
  }

  private static List<Path> requirementsFiles(final Path root) {
    final List<Path> files = new ArrayList<>();
    for (final Path dir : List.of(root, root.resolve("requirements"))) {
      if (Files.isDirectory(dir)) {
        try (Stream<Path> listed = Files.list(dir)) {
          listed
              .filter(Files::isRegularFile)
              .filter(
                  file ->
                      PythonProjectInspection.isRequirementsFile(LockFiles.fileName(file))
                          || !dir.equals(root) && LockFiles.fileName(file).endsWith(".txt"))
              .sorted()
              .forEach(files::add);
        } catch (IOException e) {
          throw new DependencyInventoryException(
              root.toString(),
              "requirements",
              "the folder cannot be read",
              "check its permissions",
              e);
        }
      }
    }
    return files;
  }
}
