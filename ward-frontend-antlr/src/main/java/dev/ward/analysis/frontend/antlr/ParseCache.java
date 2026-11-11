package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The results of the most recent parses, keyed by unit (path, language and text, so a unit whose
 * text changed is a different key). The cache is bounded by weight, the length of the source text
 * plus a fixed cost per entry, since a syntax tree grows with its text. The least recently used
 * entries are dropped first, and the newest entry is always kept. Safe for concurrent use: threads
 * asking for the same unit wait for the one parse of it.
 */
final class ParseCache {

  private static final Logger LOG = Logger.getLogger(ParseCache.class.getName());
  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;

  /** What an entry weighs besides its text, so that many tiny units are bounded too. */
  private static final int ENTRY_COST = 1024;

  private final Map<SourceUnit, ParseResult> recent =
      new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true);
  private final ReentrantLock lock = new ReentrantLock();
  private final long budget;
  private long weight;

  /**
   * Creates an empty cache.
   *
   * @param budget the total weight kept, in characters of source text
   */
  ParseCache(final long budget) {
    this.budget = budget;
  }

  /**
   * Returns the cached result of {@code unit}, or parses it with {@code parser}, caches it and
   * drops the least recently used entries over budget. The parser is not thread-safe, so it runs
   * under the cache's lock.
   */
  ParseResult parse(final SourceUnit unit, final Function<SourceUnit, ParseResult> parser) {
    lock.lock();
    try {
      final ParseResult known = recent.get(unit);
      if (known != null) {
        return known;
      }
      final ParseResult parsed = parser.apply(unit);
      put(unit, parsed);
      return parsed;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the cached result of {@code unit}, or {@code null} when it is not cached. */
  ParseResult get(final SourceUnit unit) {
    lock.lock();
    try {
      return recent.get(unit);
    } finally {
      lock.unlock();
    }
  }

  private void put(final SourceUnit unit, final ParseResult result) {
    if (recent.put(unit, result) == null) {
      weight += weigh(unit);
    }
    final Iterator<SourceUnit> eldest = recent.keySet().iterator();
    while (weight > budget && recent.size() > 1) {
      final SourceUnit dropped = eldest.next();
      eldest.remove();
      weight -= weigh(dropped);
      LOG.fine(() -> "parse cache dropped " + dropped.path());
    }
  }

  private static long weigh(final SourceUnit unit) {
    return (long) unit.text().length() + ENTRY_COST;
  }
}
