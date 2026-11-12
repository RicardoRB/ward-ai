package dev.ward.analysis.domain.openapi;

import java.util.Optional;

/**
 * Reads the text of a file of the project. The loader never reads a file by any other route, so a
 * test hands it a map and a Git-revision reader hands it the files of that revision.
 */
@FunctionalInterface
public interface ProjectFileReader {

  /**
   * Reads one file.
   *
   * @param path the project-relative path with {@code /} separators, already normalized and inside
   *     the project root
   * @return the text, or empty when the file does not exist or cannot be read
   */
  Optional<String> read(String path);
}
