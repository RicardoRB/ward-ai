package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves one module's declared dependencies against a lockfile: a declared package is direct,
 * what a sibling workspace package declares is transitive, and every package reachable from either
 * is an entry with its edges.
 */
final class NodeResolution {
  private static final String PACKAGE_JSON = "package.json";

  private final ModuleBuilder builder;
  private final Project project;
  private final NodeLock lock;
  private final String lockPath;
  private final String offset;
  private final Map<String, NodeMember> workspace;
  private final List<PackageId> seeds = new ArrayList<>();
  private final Set<String> visited = new HashSet<>();

  /**
   * Creates the resolution of one module.
   *
   * @param lockPath the project-relative lockfile
   * @param offset the project root's path inside the lockfile's directory
   * @param workspace every module by package name
   */
  NodeResolution(
      final Project project,
      final NodeLock lock,
      final String lockPath,
      final String offset,
      final Map<String, NodeMember> workspace) {
    this.builder = new ModuleBuilder(lockPath, true);
    this.project = project;
    this.lock = lock;
    this.lockPath = lockPath;
    this.offset = offset;
    this.workspace = workspace;
  }

  /** Resolves {@code member} completely. */
  ModuleDependencies resolve(final NodeMember member) {
    member.declared().forEach((name, declared) -> declare(member, name, declared));
    reach();
    return builder.build(member.path());
  }

  private void declare(
      final NodeMember member, final String name, final NodeMember.Declared declared) {
    final NodeMember sibling = workspace.get(name);
    if (sibling != null) {
      siblings(sibling, declared.scope());
      return;
    }
    final String manifest = LockFiles.join(member.path(), PACKAGE_JSON);
    final PackageId id =
        lock.directs()
            .resolve(LockFiles.join(offset, member.path()), name, declared.range())
            .orElse(null);
    if (id != null) {
      builder.direct(id, declared.scope(), manifest);
      seeds.add(id);
    } else if (!declared.peer()) {
      throw new DependencyInventoryException(
          project.root().toString(),
          lockPath,
          "the lockfile has no entry for " + name + " declared in " + manifest,
          "run `" + project.buildTool().executable() + " install`");
    }
  }

  /** Adds what a sibling workspace package resolves, as transitive packages of {@code scope}. */
  private void siblings(final NodeMember sibling, final DependencyScope scope) {
    if (!visited.add(sibling.path())) {
      return;
    }
    sibling
        .declared()
        .forEach(
            (name, declared) -> {
              final NodeMember nested = workspace.get(name);
              if (declared.scope() == DependencyScope.DEVELOPMENT) {
                return;
              }
              if (nested != null) {
                siblings(nested, scope);
                return;
              }
              lock.directs()
                  .resolve(LockFiles.join(offset, sibling.path()), name, declared.range())
                  .ifPresent(
                      id -> {
                        builder.scope(id, scope);
                        seeds.add(id);
                      });
            });
  }

  /** Adds every package reachable from the seeds, with its edges. */
  private void reach() {
    final Deque<PackageId> work = new ArrayDeque<>(seeds);
    final Set<PackageId> seen = new HashSet<>();
    while (!work.isEmpty()) {
      final PackageId id = work.poll();
      if (seen.add(id)) {
        final NodeLock.Locked locked = lock.packages().get(id);
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
}
