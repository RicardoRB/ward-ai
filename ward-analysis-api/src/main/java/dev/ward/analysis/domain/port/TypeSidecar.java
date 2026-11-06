package dev.ward.analysis.domain.port;

import java.nio.file.Path;
import java.util.Optional;

/**
 * An optional, out-of-process source of type information for one language. Starting it may fail; a
 * run then has no types for that language. Closing it ends the process.
 */
public interface TypeSidecar {

  /**
   * Starts the sidecar and returns its facts, or empty when it cannot start or answer within its
   * limits.
   */
  Optional<TypeFacts> start();

  /**
   * Starts the sidecar for a project whose Java classpath file is {@code classpathFile}, if it has
   * one. Sidecars that do not use a classpath start as {@link #start()} does.
   */
  default Optional<TypeFacts> start(final Optional<Path> classpathFile) {
    return start();
  }

  /** Stops the sidecar if it runs. Calling it again has no effect. */
  void close();
}
