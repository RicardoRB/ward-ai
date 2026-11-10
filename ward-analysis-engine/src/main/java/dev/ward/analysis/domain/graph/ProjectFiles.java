package dev.ward.analysis.domain.graph;

import java.util.Objects;
import java.util.Set;

/**
 * The files of a run that a whole-project rule may report, and the entry files that count as used
 * without an importer.
 *
 * @param files the project-root-relative paths of the analysed files
 * @param entries the project-root-relative paths declared as entry points by manifests
 */
public record ProjectFiles(Set<String> files, Set<String> entries) {

  /** No files and no entries: a whole-project rule reports nothing over it. */
  public static final ProjectFiles EMPTY = new ProjectFiles(Set.of(), Set.of());

  /**
   * Copies the sets.
   *
   * @throws NullPointerException if a field is missing
   */
  public ProjectFiles {
    Objects.requireNonNull(files, "files");
    Objects.requireNonNull(entries, "entries");
    files = Set.copyOf(files);
    entries = Set.copyOf(entries);
  }
}
