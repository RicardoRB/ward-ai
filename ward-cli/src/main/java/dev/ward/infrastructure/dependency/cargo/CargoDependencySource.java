package dev.ward.infrastructure.dependency.cargo;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.project.rust.CargoWorkspaces;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the inventory of a Cargo project without running cargo: {@code Cargo.lock} gives the
 * packages and their edges, and the {@code Cargo.toml} of the root and of each workspace member
 * gives what each module declares. Path and workspace crates are modules, not packages.
 */
public final class CargoDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(CargoDependencySource.class.getName());
  private static final String LOCK = "Cargo.lock";
  private static final String FIX = "cargo generate-lockfile";

  @Override
  public DependencyInventory read(final Project project) {
    final Path root = project.root();
    final List<CargoManifest> members = new ArrayList<>();
    members.add(CargoManifest.read(root, root, project));
    CargoWorkspaces.members(root)
        .forEach(dir -> members.add(CargoManifest.read(root, dir, project)));
    final Path lockFile = LockFiles.rootWith(project, List.of(LOCK)).resolve(LOCK);
    final DependencyInventory inventory;
    if (Files.isRegularFile(lockFile)) {
      inventory = locked(project, lockFile, members);
    } else if (members.stream().anyMatch(member -> !member.declared().isEmpty())) {
      throw new DependencyInventoryException(
          root.toString(),
          LOCK,
          "the project declares dependencies and has no lockfile",
          "run `" + FIX + "`"
      );
    } else {
      inventory =
          new DependencyInventory(
              members.stream().map(m -> new ModuleDependencies(m.path(), List.of())).toList()
          );
    }
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-source tool=CARGO modules="
                + inventory.modules().size()
                + " entries="
                + inventory.entryCount()
    );
    return inventory;
  }

  private static DependencyInventory locked(
      final Project project,
      final Path lockFile,
      final List<CargoManifest> members
  ) {
    final String label = LockFiles.relative(project.root(), lockFile);
    final CargoLockfile lock = parse(project, lockFile, label);
    return new DependencyInventory(
        members.stream().map(member -> module(project, lock, label, member)).toList()
    );
  }

  private static CargoLockfile parse(final Project project, final Path file, final String label) {
    try {
      return CargoLockfile.read(Files.readString(file));
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          "it cannot be read",
          "check the file's permissions",
          e
      );
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          e.getMessage(),
          "run `" + FIX + "` with a supported version",
          e
      );
    }
  }

  private static ModuleDependencies module(
      final Project project,
      final CargoLockfile lock,
      final String label,
      final CargoManifest member
  ) {
    final ModuleBuilder builder = new ModuleBuilder(label, true);
    final List<PackageId> seeds = new ArrayList<>();
    member
        .declared()
        .forEach(
            (name, scope) -> {
              final List<PackageId> ids = lock.byName().get(name);
              if (ids == null) {
                if (!lock.local().contains(name)) {
                  throw new DependencyInventoryException(
                      project.root().toString(),
                      label,
                      "the lockfile has no entry for " + name + " declared in " + member.manifest(),
                      "run `" + FIX + "`"
                  );
                }
                return;
              }
              ids.forEach(id -> builder.direct(id, scope, member.manifest()));
              seeds.addAll(ids);
            }
        );
    reach(builder, lock, seeds);
    return builder.build(member.path());
  }

  private static void reach(
      final ModuleBuilder builder,
      final CargoLockfile lock,
      final List<PackageId> seeds
  ) {
    final Deque<PackageId> work = new ArrayDeque<>(seeds);
    final Set<PackageId> seen = new HashSet<>();
    while (!work.isEmpty()) {
      final PackageId id = work.poll();
      if (!seen.add(id)) {
        continue;
      }
      final CargoLockfile.Locked locked = lock.packages().get(id);
      builder.add(id, locked.released());
      for (final PackageId child : locked.requires()) {
        builder.edge(id, child);
        work.add(child);
      }
    }
  }
}
