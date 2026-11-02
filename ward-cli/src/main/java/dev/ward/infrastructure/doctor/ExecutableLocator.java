package dev.ward.infrastructure.doctor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Finds an executable on the PATH of the running process. */
final class ExecutableLocator {
  private ExecutableLocator() {}

  /**
   * Returns the first PATH directory that holds {@code executable}, or {@code null} when none does.
   */
  static Path onPath(final String executable) {
    final String path = System.getenv("PATH");
    if (path == null) {
      return null;
    }
    return Arrays.stream(path.split(File.pathSeparator))
        .map(Path::of)
        .map(directory -> directory.resolve(executable))
        .filter(Files::exists)
        .findFirst()
        .orElse(null);
  }
}
