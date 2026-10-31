package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Outbound contract for the packages that a workspace root lists as members. */
public interface WorkspaceMembers {
  /** Returns the members of the workspace rooted at {@code root}, sorted by directory. */
  List<WorkspaceMember> members(Path root);

  /**
   * Returns the members of the workspace rooted at {@code root} that hold another language's build
   * file, each as a project of that language. Empty unless the workspace has such members.
   */
  default List<WorkspaceMember> ownLanguageMembers(final Path root) {
    return List.of();
  }

  /** Returns the name of the member whose directory holds {@code path}, or empty when none does. */
  Optional<String> packageOf(Path root, Path path);

  /** Returns members that no workspace lists: a project that has none. */
  static WorkspaceMembers none() {
    return new WorkspaceMembers() {
      @Override
      public List<WorkspaceMember> members(final Path root) {
        return List.of();
      }

      @Override
      public Optional<String> packageOf(final Path root, final Path path) {
        return Optional.empty();
      }
    };
  }
}
