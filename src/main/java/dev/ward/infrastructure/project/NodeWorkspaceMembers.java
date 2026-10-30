package dev.ward.infrastructure.project;

import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Lists the members of a npm, pnpm, Yarn or Bun workspace from its workspace patterns. */
public final class NodeWorkspaceMembers implements WorkspaceMembers {
  private static final String TURBO_JSON = "turbo.json";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public NodeWorkspaceMembers() {}

  @Override
  public List<WorkspaceMember> members(final Path root) {
    final TaskRunner runner =
        Files.isRegularFile(root.resolve(TURBO_JSON)) ? TaskRunner.TURBO_MEMBER : TaskRunner.NONE;
    return NodeWorkspaces.memberDirectories(root, NodeProjectInspection.manifest(root)).stream()
        .map(
            dir ->
                new WorkspaceMember(
                    name(root, dir),
                    dir,
                    NodeProjectInspection.project(dir).withTaskRunner(runner),
                    scripts(dir)))
        .toList();
  }

  @Override
  public Optional<String> packageOf(final Path root, final Path path) {
    return members(root).stream()
        .filter(member -> path.startsWith(member.directory()))
        .max(Comparator.comparingInt(member -> member.directory().getNameCount()))
        .map(WorkspaceMember::name);
  }

  /** Returns the command of each string script in the package.json of {@code dir}. */
  private static Map<String, String> scripts(final Path dir) {
    final Map<String, String> scripts = new LinkedHashMap<>();
    if (NodeProjectInspection.manifest(dir).get("scripts") instanceof Map<?, ?> declared) {
      declared.forEach(
          (name, command) -> {
            if (command instanceof String text) {
              scripts.put(String.valueOf(name), text);
            }
          });
    }
    return scripts;
  }

  /** Returns the package.json name of {@code dir}, or its path below {@code root} when unnamed. */
  private static String name(final Path root, final Path dir) {
    return NodeProjectInspection.manifest(dir).get("name") instanceof String name
        ? name
        : root.relativize(dir).toString().replace('\\', '/');
  }
}
