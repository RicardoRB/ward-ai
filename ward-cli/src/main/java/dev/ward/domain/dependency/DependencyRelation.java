package dev.ward.domain.dependency;

/** Whether a module's own manifest declares a package. */
public enum DependencyRelation {
  /** The manifest of the module declares the package. */
  DIRECT,
  /** The package is only reachable through other packages. */
  TRANSITIVE
}
