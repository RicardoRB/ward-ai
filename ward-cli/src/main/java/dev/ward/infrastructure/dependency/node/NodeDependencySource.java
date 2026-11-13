package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final String BUN_BINARY = "bun.lockb";

  private final NodeLockfiles lockfiles;

  /** Creates the source; Dagger injects the codec. */
  @Inject
  public NodeDependencySource(final JsonCodec codec) {
    this.lockfiles = new NodeLockfiles(codec);
  }

  @Override
  public DependencyInventory read(final Project project) {
    final Path root = project.root();
    final List<NodeMember> members = NodeMember.read(root);
    final Path lockRoot = NodeLockfiles.root(project);
    final String lockName = NodeLockfiles.existing(project.buildTool(), lockRoot).orElse("");
    if (lockName.isEmpty()) {
      return missing(project, members, lockRoot);
    }
    final String lockPath = LockFiles.relative(root, lockRoot.resolve(lockName));
    final NodeLock lock = lockfiles.parse(project, lockRoot.resolve(lockName), lockPath);
    final String offset = LockFiles.relative(lockRoot, root);
    final Map<String, NodeMember> workspace = new HashMap<>();
    members.stream()
        .filter(member -> !member.name().isEmpty())
        .forEach(member -> workspace.put(member.name(), member));
    final DependencyInventory inventory =
        new DependencyInventory(
            members.stream()
                .map(
                    member ->
                        new NodeResolution(project, lock, lockPath, offset, workspace)
                            .resolve(member))
                .toList());
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-source source="
                + lockPath
                + " modules="
                + inventory.modules().size()
                + " entries="
                + inventory.entryCount());
    return inventory;
  }

  private static DependencyInventory missing(
      final Project project, final List<NodeMember> members, final Path lockRoot) {
    if (members.stream().anyMatch(NodeMember::declaresPackages)) {
      final boolean bun = project.buildTool() == BuildTool.BUN;
      throw new DependencyInventoryException(
          project.root().toString(),
          NodeLockfiles.names(project.buildTool()).getLast(),
          "the project declares dependencies and has no lockfile"
              + (bun && Files.isRegularFile(lockRoot.resolve(BUN_BINARY))
                  ? "; " + BUN_BINARY + " is binary and cannot be read"
                  : ""),
          bun
              ? "run `bun install --save-text-lockfile`"
              : "run `" + project.buildTool().executable() + " install`");
    }
    LOGGER.log(Level.FINE, "event=dependency-source source=none reason=no-dependencies");
    return new DependencyInventory(
        members.stream().map(m -> new ModuleDependencies(m.path(), List.of())).toList());
  }
}
