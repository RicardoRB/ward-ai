package dev.ward.infrastructure.project;

import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Lists the members of a npm, pnpm, Yarn or Bun workspace from its workspace patterns. */
public final class NodeWorkspaceMembers implements WorkspaceMembers {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public NodeWorkspaceMembers() {}

  @Override
  public List<WorkspaceMember> members(final Path root) {
    return NodeWorkspaces.memberDirectories(root, NodeProjectInspection.manifest(root)).stream()
        .map(dir -> new WorkspaceMember(name(root, dir), dir, NodeProjectInspection.project(dir)))
        .toList();
  }

  @Override
  public Optional<String> packageOf(final Path root, final Path path) {
    return members(root).stream()
        .filter(member -> path.startsWith(member.directory()))
        .max(Comparator.comparingInt(member -> member.directory().getNameCount()))
        .map(WorkspaceMember::name);
  }

  /** Returns the package.json name of {@code dir}, or its path below {@code root} when unnamed. */
  private static String name(final Path root, final Path dir) {
    return NodeProjectInspection.manifest(dir).get("name") instanceof String name
        ? name
        : root.relativize(dir).toString().replace('\\', '/');
  }
}
