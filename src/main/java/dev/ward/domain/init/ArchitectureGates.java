package dev.ward.domain.init;

import java.util.Set;

/** The gates that enforce an architecture style, and so take a style and generated rules. */
public final class ArchitectureGates {
  /** {@code archunit} for the JVM and {@code dependency-cruiser} for Node. */
  public static final Set<String> IDS = Set.of("archunit", "dependency-cruiser");

  private ArchitectureGates() {}
}
