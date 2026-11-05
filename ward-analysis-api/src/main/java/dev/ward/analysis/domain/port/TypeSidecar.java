package dev.ward.analysis.domain.port;

import java.util.Optional;

/**
 * An optional, out-of-process source of type information for one language. Starting it may fail; a
 * run then has no types for that language. Closing it ends the process.
 */
public interface TypeSidecar {

  /**
   * Starts the sidecar and returns its resolver, or empty when it cannot start or answer within its
   * limits.
   */
  Optional<TypeResolver> start();

  /** Stops the sidecar if it runs. Calling it again has no effect. */
  void close();
}
