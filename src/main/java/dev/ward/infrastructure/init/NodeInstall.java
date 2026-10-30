package dev.ward.infrastructure.init;

import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The install a Node project runs after its package.json changes, and the lock file it rewrites.
 */
final class NodeInstall {
  private NodeInstall() {}

  static List<String> installCommand(final Project project) {
    return List.of(project.buildExecutable(), "install");
  }

  /** The lock file the install writes; Bun keeps a binary {@code bun.lockb} it already has. */
  static Path lockFile(final Project project) {
    final Path root = project.root();
    return switch (project.buildTool()) {
      case PNPM -> root.resolve("pnpm-lock.yaml");
      case YARN -> root.resolve("yarn.lock");
      case BUN ->
          Files.isRegularFile(root.resolve("bun.lockb"))
              ? root.resolve("bun.lockb")
              : root.resolve("bun.lock");
      default -> root.resolve("package-lock.json");
    };
  }
}
