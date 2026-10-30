package dev.ward.domain.init;

import java.util.Set;

/** The gates that enforce an architecture style, and so take a style and generated rules. */
public final class ArchitectureGates {
  /**
   * {@code archunit} for the JVM, {@code dependency-cruiser} for Node and {@code import-linter} for
   * Python.
   */
  public static final Set<String> IDS = Set.of("archunit", "dependency-cruiser", "import-linter");

  private ArchitectureGates() {}
}
