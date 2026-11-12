package dev.ward.domain.dependency;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Port for the stored graphs that a build tool resolved for the project at a root, keyed by the
 * content of its build files. An entry older than the freshness limit counts as missing.
 */
public interface GraphCache {
  /** Returns the stored text for {@code key} if it exists and is fresh; never throws. */
  Optional<String> load(Path projectRoot, String key);

  /** Stores {@code text} for {@code key}, replacing any earlier entry. */
  void store(Path projectRoot, String key, String text);
}
