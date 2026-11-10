package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.Map;

/**
 * A package that a workspace root lists as a member.
 *
 * @param name the package name, or its path below the workspace root when it has none
 * @param directory the member directory
 * @param project the member inspected as a project of its own
 * @param scripts the command of each script in the member's package.json, by script name
 */
public record WorkspaceMember(
    String name, Path directory, Project project, Map<String, String> scripts) {
  /** Creates a member whose package.json has no scripts. */
  public WorkspaceMember(final String name, final Path directory, final Project project) {
    this(name, directory, project, Map.of());
  }

  /** Copies the scripts. */
  public WorkspaceMember {
    scripts = Map.copyOf(scripts);
  }
}
