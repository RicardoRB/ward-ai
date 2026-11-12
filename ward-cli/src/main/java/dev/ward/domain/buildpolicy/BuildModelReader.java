package dev.ward.domain.buildpolicy;

import java.nio.file.Path;
import java.util.List;

/** Reads the build files of one build tool in a directory of the project. */
public interface BuildModelReader {
  /**
   * Returns the project-relative directories, with {@code /}, of the modules that the build files
   * in {@code dir} list.
   *
   * @throws BuildFileException if a build file cannot be read
   */
  List<String> modules(Path root, String dir);

  /**
   * Reads the declarations of the build files in {@code dir}, a project-relative directory; empty
   * for the project root.
   *
   * @throws BuildFileException if a build file cannot be read or parsed
   */
  BuildModel read(Path root, String dir);
}
