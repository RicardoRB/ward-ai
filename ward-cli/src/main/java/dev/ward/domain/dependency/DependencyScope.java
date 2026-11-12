package dev.ward.domain.dependency;

/** Where a package is needed, ordered from the widest scope to the narrowest. */
public enum DependencyScope {
  /** Shipped with the module: Maven compile and runtime, npm dependencies, Python main. */
  RUNTIME,
  /** Needed to compile and supplied by the container: Maven provided, Gradle compileOnly. */
  PROVIDED,
  /** Needed by tests only. */
  TEST,
  /** Needed by development tools only: dev dependencies, annotation processors. */
  DEVELOPMENT;

  /** Returns the wider of the two scopes. */
  public static DependencyScope widest(final DependencyScope first, final DependencyScope second) {
    return first.compareTo(second) <= 0 ? first : second;
  }
}
