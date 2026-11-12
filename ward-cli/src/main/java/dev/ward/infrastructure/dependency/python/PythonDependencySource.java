package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.infrastructure.dependency.LockFiles;
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

/**
 * Reads the inventory of a Python project without running a package manager: {@code uv.lock} for uv
 * (one module per workspace member), {@code poetry.lock} for Poetry, and the pinned {@code
 * requirements*.txt} files for pip.
 */
public final class PythonDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(PythonDependencySource.class.getName());
  private static final String UV_LOCK = "uv.lock";
  private static final String POETRY_LOCK = "poetry.lock";

  @Override
  public DependencyInventory read(final dev.ward.domain.project.Project project) {
    final DependencyInventory inventory =
        switch (project.buildTool()) {
          case UV -> locked(project, UV_LOCK, "uv lock");
          case POETRY -> locked(project, POETRY_LOCK, "poetry lock");
          default -> PipRequirementsSource.read(project);
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
    if (UV_LOCK.equals(lockName)) {
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
      return UV_LOCK.equals(file.getFileName().toString())
          ? UvLockfile.read(text)
          : PoetryLockfile.read(text);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(), label, "it cannot be read", "check the file's permissions", e);
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          e.getMessage(),
          "run `" + fix + "` with a supported version",
          e);
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
}
