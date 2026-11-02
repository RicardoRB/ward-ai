package dev.ward.domain.init;

import java.util.Set;

/** The gates that enforce an architecture style, and so take a style and generated rules. */
public final class ArchitectureGates {
  /**
   * {@code archunit} for the JVM, {@code dependency-cruiser} for Node, {@code import-linter} for
   * Python and {@code harmonize} for Swift.
   */
  public static final Set<String> IDS =
      Set.of("archunit", "dependency-cruiser", "import-linter", "harmonize");

  /** The Ward-owned package whose tests are the Harmonize architecture rules. */
  public static final String HARMONIZE_PACKAGE = ".ward/swift-architecture";

  private ArchitectureGates() {}
}
