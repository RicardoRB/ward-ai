package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The results of the most recent parses, keyed by unit (path, language and text, so a unit whose
 * text changed is a different key). The cache is bounded by weight, the length of the source text
 * plus a fixed cost per entry, since a syntax tree grows with its text. The least recently used
 * entries are dropped first, and the newest entry is always kept. Safe for concurrent use: the lock
 * guards the entries only, so threads parse different units at the same time, and threads asking
 * for the same unit wait for the one parse of it.
 */
final class ParseCache {
  private static final Logger LOG = Logger.getLogger(ParseCache.class.getName());
  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;

  /** What an entry weighs besides its text, so that many tiny units are bounded too. */
  private static final int ENTRY_COST = 1024;

  private final Map<SourceUnit, ParseResult> recent =
      new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true);
  private final Map<SourceUnit, CompletableFuture<ParseResult>> parsing = new HashMap<>();
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
   * drops the least recently used entries over budget. The parser runs outside the cache's lock,
   * so it must be safe to call from several threads; a thread that asks for a unit another thread
   * is parsing waits for that result.
   */
  ParseResult parse(final SourceUnit unit, final Function<SourceUnit, ParseResult> parser) {
    final CompletableFuture<ParseResult> mine = new CompletableFuture<>();
    final CompletableFuture<ParseResult> other;
    lock.lock();
    try {
      final ParseResult known = recent.get(unit);
      if (known != null) {
        return known;
      }
      other = parsing.putIfAbsent(unit, mine);
    } finally {
      lock.unlock();
    }
    if (other == null) {
      return parseAndPublish(unit, parser, mine);
    }
    try {
      return other.join();
    } catch (final CancellationException failedElsewhere) {
      // The other thread's parse failed; this thread parses and fails or succeeds by itself.
      return parse(unit, parser);
    }
  }

  private ParseResult parseAndPublish(
      final SourceUnit unit,
      final Function<SourceUnit,
      ParseResult> parser,
      final CompletableFuture<ParseResult> mine
  ) {
    ParseResult parsed = null;
    try {
      parsed = parser.apply(unit);
      return parsed;
    } finally {
      lock.lock();
      try {
        parsing.remove(unit);
        if (parsed != null) {
          put(unit, parsed);
        }
      } finally {
        lock.unlock();
      }
      if (parsed == null) {
        mine.cancel(false);
      } else {
        mine.complete(parsed);
      }
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
