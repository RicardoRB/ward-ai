package dev.ward.infrastructure.project;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decides when a Node workspace member is its own project of another language. */
final class OwnLanguageMembers {
  private OwnLanguageMembers() {}

  /**
   * Returns whether a Node workspace of an ancestor of {@code dir}, below the Git top-level, lists
   * {@code dir} by its patterns, whatever build files {@code dir} holds.
   */
  static boolean listedByAncestor(final Path dir) {
    for (Path ancestor = dir.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
      if (Files.isRegularFile(ancestor.resolve(NodeProjectInspection.PACKAGE_JSON))
          && NodeWorkspaces.listsByPattern(ancestor, dir)) {
        return true;
      }
      if (Files.exists(ancestor.resolve(FileSystemProjectInspection.GIT))) {
        return false;
      }
    }
    return false;
  }

  /**
   * Returns {@code project} as detected from {@code dir}, except that a Node project holding a
   * Python build file that a Node workspace lists is the Python project of that member.
   */
  static Project ownLanguage(final Path dir, final Project project) {
    return project.toolchain() == Toolchain.NODE
            && PythonProjectInspection.hasMarker(dir)
            && listedByAncestor(dir)
        ? PythonProjectInspection.project(dir)
        : project;
  }
}
