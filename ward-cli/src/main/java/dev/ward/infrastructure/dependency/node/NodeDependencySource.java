package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.node.NodeProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaces;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads the inventory of a Node project from the lockfile of its package manager, without running
 * the package manager. Each workspace member is a module whose own {@code package.json} decides
 * which packages are direct; sibling workspace packages are not entries, the packages they resolve
 * are.
 */
public final class NodeDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(NodeDependencySource.class.getName());
  private static final String PACKAGE_JSON = "package.json";
  private static final String BUN_BINARY = "bun.lockb";
  private static final Map<String, DependencyScope> SECTIONS = sections();

  private final JsonCodec codec;

  /** Creates the source; Dagger injects the codec. */
  @Inject
  public NodeDependencySource(final JsonCodec codec) {
    this.codec = codec;
  }

  private static Map<String, DependencyScope> sections() {
    final Map<String, DependencyScope> sections = new LinkedHashMap<>();
    sections.put("dependencies", DependencyScope.RUNTIME);
    sections.put("optionalDependencies", DependencyScope.RUNTIME);
    sections.put("peerDependencies", DependencyScope.RUNTIME);
    sections.put("devDependencies", DependencyScope.DEVELOPMENT);
    return sections;
  }

  /** One module: where it is and what its manifest declares. */
  private record Member(String path, String name, Map<String, Declared> declared) {}

  /** One declared dependency. */
  private record Declared(String range, DependencyScope scope, boolean peer) {}

  @Override
  public DependencyInventory read(final Project project) {
    final Path root = project.root();
    final List<Member> members = members(root);
    final Path lockRoot = lockRoot(project);
    final String lockName = lockName(project.buildTool(), lockRoot);
    final boolean declares = members.stream().anyMatch(NodeDependencySource::declaresPackages);
    if (lockName == null) {
      return missing(project, members, declares, lockRoot);
    }
    final String lockPath = relative(root, lockRoot.resolve(lockName));
    final NodeLock lock = parse(project, lockRoot.resolve(lockName), lockPath);
    final String offset = relative(lockRoot, root);
    final Map<String, Member> workspace = new HashMap<>();
    members.stream()
        .filter(member -> !member.name().isEmpty())
        .forEach(m -> workspace.put(m.name(), m));
    final List<ModuleDependencies> modules = new ArrayList<>();
    for (final Member member : members) {
      modules.add(module(project, lock, lockPath, offset, workspace, member));
    }
    final DependencyInventory inventory = new DependencyInventory(modules);
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-source source="
                + lockPath
                + " modules="
                + modules.size()
                + " entries="
                + inventory.entryCount());
    return inventory;
  }

  private DependencyInventory missing(
      final Project project,
      final List<Member> members,
      final boolean declares,
      final Path lockRoot) {
    if (declares) {
      final String install = project.buildTool().executable() + " install";
      throw new DependencyInventoryException(
          project.root().toString(),
          lockFileLabel(project.buildTool()),
          "the project declares dependencies and has no lockfile"
              + (project.buildTool() == BuildTool.BUN
                      && Files.isRegularFile(lockRoot.resolve(BUN_BINARY))
                  ? "; " + BUN_BINARY + " is binary and cannot be read"
                  : ""),
          project.buildTool() == BuildTool.BUN
              ? "run `bun install --save-text-lockfile`"
              : "run `" + install + "`");
    }
    LOGGER.log(Level.FINE, "event=dependency-source source=none reason=no-dependencies");
    return new DependencyInventory(
        members.stream().map(m -> new ModuleDependencies(m.path(), List.of())).toList());
  }

  private ModuleDependencies module(
      final Project project,
      final NodeLock lock,
      final String lockPath,
      final String offset,
      final Map<String, Member> workspace,
      final Member member) {
    final ModuleBuilder builder = new ModuleBuilder(lockPath, true);
    final String lockMember = join(offset, member.path());
    final String manifest = join(member.path(), PACKAGE_JSON);
    final List<PackageId> seeds = new ArrayList<>();
    final Set<String> visited = new HashSet<>();
    member
        .declared()
        .forEach(
            (name, declared) -> {
              final Member sibling = workspace.get(name);
              if (sibling != null) {
                siblings(
                    builder, lock, offset, workspace, sibling, declared.scope(), visited, seeds);
                return;
              }
              final PackageId id =
                  lock.directs().resolve(lockMember, name, declared.range()).orElse(null);
              if (id == null && declared.peer()) {
                return;
              }
              if (id == null) {
                throw new DependencyInventoryException(
                    project.root().toString(),
                    lockPath,
                    "the lockfile has no entry for " + name + " declared in " + manifest,
                    "run `" + project.buildTool().executable() + " install`");
              }
              builder.direct(id, declared.scope(), manifest);
              seeds.add(id);
            });
    reach(builder, lock, seeds);
    return builder.build(member.path());
  }

  /** Adds what a sibling workspace package resolves, as transitive packages of {@code scope}. */
  private void siblings(
      final ModuleBuilder builder,
      final NodeLock lock,
      final String offset,
      final Map<String, Member> workspace,
      final Member sibling,
      final DependencyScope scope,
      final Set<String> visited,
      final List<PackageId> seeds) {
    if (!visited.add(sibling.path())) {
      return;
    }
    sibling
        .declared()
        .forEach(
            (name, declared) -> {
              if (declared.scope() == DependencyScope.DEVELOPMENT) {
                return;
              }
              final Member nested = workspace.get(name);
              if (nested != null) {
                siblings(builder, lock, offset, workspace, nested, scope, visited, seeds);
                return;
              }
              lock.directs()
                  .resolve(join(offset, sibling.path()), name, declared.range())
                  .ifPresent(
                      id -> {
                        builder.scope(id, scope);
                        seeds.add(id);
                      });
            });
  }

  /** Adds every package reachable from {@code seeds}, with its edges. */
  private static void reach(
      final ModuleBuilder builder, final NodeLock lock, final List<PackageId> seeds) {
    final Deque<PackageId> work = new ArrayDeque<>(seeds);
    final Set<PackageId> seen = new HashSet<>();
    while (!work.isEmpty()) {
      final PackageId id = work.poll();
      if (!seen.add(id)) {
        continue;
      }
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

  private NodeLock parse(final Project project, final Path file, final String label) {
    final String text;
    try {
      text = Files.readString(file);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(), label, "it cannot be read", "check the file's permissions");
    }
    try {
      return switch (project.buildTool()) {
        case PNPM -> PnpmLockfile.read(text);
        case YARN -> yarn(text);
        case BUN -> BunLockfile.read(text, codec);
        default -> NpmLockfile.read(text, codec);
      };
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          e.getMessage(),
          "run `" + project.buildTool().executable() + " install` with a supported version");
    }
  }

  private static NodeLock yarn(final String text) {
    if (text.contains(YarnClassicLockfile.HEADER)) {
      return YarnClassicLockfile.read(text);
    }
    if (text.contains("__metadata:")) {
      return YarnBerryLockfile.read(text);
    }
    throw new LockfileFormatException("it is neither a Yarn classic nor a Yarn Berry lockfile");
  }

  private static boolean declaresPackages(final Member member) {
    return member.declared().values().stream().anyMatch(declared -> !declared.peer());
  }

  /** Lists the root first, then the workspace members. */
  private static List<Member> members(final Path root) {
    final List<Member> members = new ArrayList<>();
    members.add(member(root, ""));
    for (final Path dir : NodeWorkspaces.members(root)) {
      if (!dir.equals(root)) {
        members.add(member(dir, relative(root, dir)));
      }
    }
    return members;
  }

  private static Member member(final Path dir, final String path) {
    final Map<?, ?> manifest = NodeProjectInspection.manifest(dir);
    final Map<String, Declared> declared = new LinkedHashMap<>();
    SECTIONS.forEach(
        (section, scope) -> {
          if (manifest.get(section) instanceof Map<?, ?> deps) {
            deps.forEach(
                (name, range) ->
                    declared.merge(
                        String.valueOf(name),
                        new Declared(
                            String.valueOf(range), scope, "peerDependencies".equals(section)),
                        NodeDependencySource::wider));
          }
        });
    return new Member(path, manifest.get("name") instanceof String name ? name : "", declared);
  }

  private static Declared wider(final Declared first, final Declared second) {
    return new Declared(
        first.range(),
        DependencyScope.widest(first.scope(), second.scope()),
        first.peer() && second.peer());
  }

  /** Returns the directory with the lockfile: the project root or the nearest ancestor with one. */
  private static Path lockRoot(final Project project) {
    Path dir = project.root();
    while (dir != null && dir.startsWith(project.repositoryRoot())) {
      if (lockName(project.buildTool(), dir) != null) {
        return dir;
      }
      dir = dir.getParent();
    }
    return project.root();
  }

  private static String lockName(final BuildTool tool, final Path dir) {
    final List<String> names =
        switch (tool) {
          case PNPM -> List.of("pnpm-lock.yaml");
          case YARN -> List.of("yarn.lock");
          case BUN -> List.of("bun.lock");
          default -> List.of("npm-shrinkwrap.json", "package-lock.json");
        };
    return names.stream()
        .filter(name -> Files.isRegularFile(dir.resolve(name)))
        .findFirst()
        .orElse(null);
  }

  private static String lockFileLabel(final BuildTool tool) {
    return switch (tool) {
      case PNPM -> "pnpm-lock.yaml";
      case YARN -> "yarn.lock";
      case BUN -> "bun.lock";
      default -> "package-lock.json";
    };
  }

  private static String relative(final Path base, final Path file) {
    return base.relativize(file).toString().replace('\\', '/');
  }

  private static String join(final String first, final String second) {
    return first.isEmpty() ? second : second.isEmpty() ? first : first + "/" + second;
  }
}
