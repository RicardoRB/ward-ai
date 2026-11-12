package dev.ward.domain.dependency;

import java.util.Optional;

/** Port for the stored graphs that a build tool resolved, keyed by the build files' content. */
public interface GraphCache {
  /** Returns the stored text for {@code key} if it exists and is fresh; never throws. */
  Optional<String> load(String key);

  /** Stores {@code text} for {@code key}, replacing any earlier entry. */
  void store(String key, String text);
}
