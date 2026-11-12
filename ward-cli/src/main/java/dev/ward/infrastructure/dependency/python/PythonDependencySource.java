package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.project.python.PythonProjectInspection;
import dev.ward.infrastructure.project.python.PythonWorkspaces;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Reads the inventory of a Python project without running a package manager: {@code uv.lock} for uv
 * (one module per workspace member), {@code poetry.lock} for Poetry, and the pinned {@code
 * requirements*.txt} files for pip.
 */
public final class PythonDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(PythonDependencySource.class.getName());
  private static final String PYPROJECT = PythonProjectInspection.PYPROJECT;
  private static final String UV_LOCK = "uv.lock";
  private static final String POETRY_LOCK = "poetry.lock";

  @Override
  public DependencyInventory read(final dev.ward.domain.project.Project project) {
    final DependencyInventory inventory =
        switch (project.buildTool()) {
          case UV -> locked(project, UV_LOCK, "uv lock");
          case POETRY -> locked(project, POETRY_LOCK, "poetry lock");
          default -> pip(project);
        };
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-source tool="
                + project.buildTool()
                + " modules="
                + inventory.modules().size()
                + " entries="
                + inventory.entryCount());
    return inventory;
  }

  private DependencyInventory locked(
      final dev.ward.domain.project.Project project, final String lockName, final String fix) {
    final Path root = project.root();
    final List<PythonManifest> members = new ArrayList<>();
    members.add(PythonManifest.read(root, root, project));
    if (lockName.equals(UV_LOCK)) {
      PythonWorkspaces.members(root)
          .forEach(dir -> members.add(PythonManifest.read(root, dir, project)));
    }
    final Path lockRoot = LockFiles.rootWith(project, List.of(lockName));
    final Path lockFile = lockRoot.resolve(lockName);
    if (!Files.isRegularFile(lockFile)) {
      if (members.stream().anyMatch(PythonManifest::declaresPackages)) {
        throw new DependencyInventoryException(
            root.toString(),
            lockName,
            "the project declares dependencies and has no lockfile",
            "run `" + fix + "`");
      }
      return new DependencyInventory(
          members.stream().map(m -> new ModuleDependencies(m.path(), List.of())).toList());
    }
    final String label = LockFiles.relative(root, lockFile);
    final PythonLock lock = parse(project, lockFile, label, fix);
    final List<ModuleDependencies> modules = new ArrayList<>();
    for (final PythonManifest member : members) {
      modules.add(module(project, lock, label, fix, member));
    }
    return new DependencyInventory(modules);
  }

  private static PythonLock parse(
      final dev.ward.domain.project.Project project,
      final Path file,
      final String label,
      final String fix) {
    try {
      final String text = Files.readString(file);
      return file.getFileName().toString().equals(UV_LOCK)
          ? UvLockfile.read(text)
          : PoetryLockfile.read(text);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(), label, "it cannot be read", "check the file's permissions");
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          e.getMessage(),
          "run `" + fix + "` with a supported version");
    }
  }

  private static ModuleDependencies module(
      final dev.ward.domain.project.Project project,
      final PythonLock lock,
      final String label,
      final String fix,
      final PythonManifest member) {
    final ModuleBuilder builder = new ModuleBuilder(label, true);
    final Map<String, PythonLock.Resolved> directs =
        lock.directs().getOrDefault(member.lockKey(), Map.of());
    final List<PackageId> seeds = new ArrayList<>();
    member
        .declared()
        .forEach(
            (name, scope) -> {
              final PythonLock.Resolved resolved = directs.get(name);
              if (resolved == null) {
                throw new DependencyInventoryException(
                    project.root().toString(),
                    label,
                    "the lockfile has no entry for " + name + " declared in " + member.manifest(),
                    "run `" + fix + "`");
              }
              for (final PackageId id : resolved.ids()) {
                if (resolved.member()) {
                  builder.scope(id, scope);
                } else {
                  builder.direct(id, scope, member.manifest());
                }
                seeds.add(id);
              }
            });
    reach(builder, lock, seeds);
    return builder.build(member.path());
  }

  private static void reach(
      final ModuleBuilder builder, final PythonLock lock, final List<PackageId> seeds) {
    final Deque<PackageId> work = new ArrayDeque<>(seeds);
    final Set<PackageId> seen = new HashSet<>();
    while (!work.isEmpty()) {
      final PackageId id = work.poll();
      if (!seen.add(id)) {
        continue;
      }
      final PythonLock.Locked locked = lock.packages().get(id);
      builder.add(id, locked == null || locked.released());
      if (locked != null) {
        for (final PackageId child : locked.requires()) {
          builder.edge(id, child);
          work.add(child);
        }
      }
    }
  }

  private DependencyInventory pip(final dev.ward.domain.project.Project project) {
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
    final Map<PackageId, String> sources = new java.util.HashMap<>();
    for (final Path file : files) {
      final DependencyScope scope = scopeOf(file.getFileName().toString());
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
            "pin every requirement with `==`, for example with `pip-compile`");
      }
    }
    return new DependencyInventory(List.of(withSources(builder.build(""), sources)));
  }

  /** Gives each entry the file its pin was read from instead of the first file read. */
  private static ModuleDependencies withSources(
      final ModuleDependencies module, final Map<PackageId, String> sources) {
    return new ModuleDependencies(
        module.modulePath(),
        module.dependencies().stream()
            .map(
                entry ->
                    new dev.ward.domain.dependency.Dependency(
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
                      PythonProjectInspection.isRequirementsFile(file.getFileName().toString())
                          || dir != root && file.getFileName().toString().endsWith(".txt"))
              .sorted()
              .forEach(files::add);
        } catch (IOException e) {
          throw new DependencyInventoryException(
              root.toString(),
              "requirements",
              "the folder cannot be read",
              "check its permissions");
        }
      }
    }
    return files;
  }
}
