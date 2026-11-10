package dev.ward.infrastructure.format;

import java.util.List;
import java.util.Objects;

/**
 * One parsed {@code .editorconfig} file.
 *
 * @param directory the folder that holds the file, relative to the project root; empty for the root
 * @param root whether the preamble sets {@code root = true}
 * @param sections the sections in file order, copied on construction
 */
record EditorConfigFile(String directory, boolean root, List<EditorConfigSection> sections) {

  /** The file name Ward reads. */
  static final String FILE_NAME = ".editorconfig";

  /**
   * Copies the sections.
   *
   * @throws NullPointerException if a component is missing
   */
  EditorConfigFile {
    Objects.requireNonNull(directory, "directory");
    sections = List.copyOf(sections);
  }

  /**
   * Returns the path of the file relative to the project root, for messages.
   *
   * @return the relative path with {@code /} separators
   */
  String path() {
    return directory.isEmpty() ? FILE_NAME : directory + "/" + FILE_NAME;
  }
}
