package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The results of the most recent parses, keyed by unit; the least recently used is dropped first.
 */
final class ParseCache {

  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;

  private final Map<SourceUnit, ParseResult> recent;

  /**
   * Creates an empty cache.
   *
   * @param bound the number of units kept
   */
  ParseCache(final int bound) {
    this.recent =
        new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(final Map.Entry<SourceUnit, ParseResult> eldest) {
            return size() > bound;
          }
        };
  }

  /** Returns the cached result of {@code unit}, or {@code null} when it is not cached. */
  ParseResult get(final SourceUnit unit) {
    return recent.get(unit);
  }

  /** Caches the result of {@code unit}. */
  void put(final SourceUnit unit, final ParseResult result) {
    recent.put(unit, result);
  }
}
