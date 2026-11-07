package dev.ward.analysis.domain.jvm.types;

import java.util.Optional;

/**
 * A source of compiled classes: the JDK image, the jars of the classpath. A class a library does
 * not hold is unknown, never an error.
 */
@FunctionalInterface
public interface JvmLibrary {

  /**
   * Returns the class with the binary name {@code name}, such as {@code java.util.Map$Entry}.
   *
   * @param name the binary name
   * @return the class, empty when this library does not hold it or cannot read it
   */
  Optional<JvmClass> find(String name);

  /** Returns whether this library holds {@code name}; it may answer without reading the class. */
  default boolean exists(final String name) {
    return find(name).isPresent();
  }
}
