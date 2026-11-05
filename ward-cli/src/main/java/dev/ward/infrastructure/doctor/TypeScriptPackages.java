package dev.ward.infrastructure.doctor;

import java.nio.file.Files;
import java.nio.file.Path;

/** Finds the {@code typescript} package the way node does: in the project or an ancestor. */
final class TypeScriptPackages {
  private TypeScriptPackages() {}

  /** Whether {@code typescript} is installed in {@code projectRoot} or an ancestor directory. */
  static boolean resolvable(final Path projectRoot) {
    for (Path directory = projectRoot.toAbsolutePath().normalize();
        directory != null;
        directory = directory.getParent()) {
      if (Files.isRegularFile(directory.resolve("node_modules/typescript/package.json"))) {
        return true;
      }
    }
    return false;
  }
}
