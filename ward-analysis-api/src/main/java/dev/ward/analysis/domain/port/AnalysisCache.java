package dev.ward.analysis.domain.port;

import java.util.Optional;

/** Stores analysis results by string key. */
public interface AnalysisCache {

  /**
   * Reads the value stored under the key.
   *
   * @param key the cache key
   * @return the value, or empty when none is stored
   */
  Optional<String> read(String key);

  /**
   * Stores the value under the key, replacing any earlier value.
   *
   * @param key the cache key
   * @param value the value to store
   */
  void write(String key, String value);
}
