package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.Optional;

/**
 * The files a type resolver reads besides the unit it answers for: other project files, declaration
 * files in {@code node_modules} and the TypeScript library. Paths are relative to the project root
 * and use {@code /}; a path outside the project is never readable.
 */
public interface DeclarationFiles {

  /** Returns the text of the file, or empty when it is missing or unreadable. */
  Optional<String> read(String path);

  /** Returns whether the file exists. */
  boolean exists(String path);

  /** Returns the names of the entries of a directory, empty when there is none. */
  List<String> list(String directory);

  /**
   * Returns a hash of the content of the package at {@code directory}: its manifest and its
   * declaration files. The same content has the same hash on every machine.
   */
  Optional<String> packageHash(String directory);
}
