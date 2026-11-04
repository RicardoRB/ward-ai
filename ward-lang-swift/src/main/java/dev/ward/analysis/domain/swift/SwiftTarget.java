package dev.ward.analysis.domain.swift;

import java.util.List;
import java.util.Objects;

/**
 * A module of a Swift project: the directories and files it owns, and the modules it declares it
 * depends on.
 *
 * @param name the module name, which an import uses
 * @param directories the directories whose files belong to the module, relative to the project
 * @param files the files that belong to the module, relative to the project
 * @param dependencies the names of the modules this module declares as dependencies
 */
public record SwiftTarget(
    String name, List<String> directories, List<String> files, List<String> dependencies) {

  /**
   * Copies the collections and validates the name.
   *
   * @throws NullPointerException if a field is missing
   */
  public SwiftTarget {
    Objects.requireNonNull(name, "name");
    directories = List.copyOf(directories);
    files = List.copyOf(files);
    dependencies = List.copyOf(dependencies);
  }
}
