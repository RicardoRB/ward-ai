package dev.ward.infrastructure.rule.catalog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one tool reported on the fixtures: the positions by fixture and tool id, and the reasons a
 * fixture or an id cannot be compared. A fixture is {@code <rule>/violating} or {@code
 * <rule>/passing}.
 */
final class ParityRun {

  private final Map<String, Set<String>> found = new HashMap<>();
  private final Map<String, String> fixtureReasons = new HashMap<>();
  private final Map<String, String> idReasons = new HashMap<>();

  /** Records a finding of {@code id} at {@code line} and {@code column} (0 when unknown). */
  void add(final String fixture, final String id, final int line, final int column) {
    found.computeIfAbsent(fixture + "|" + id, key -> new TreeSet<>()).add(line + ":" + column);
  }

  /** Returns the positions of {@code id} on {@code fixture}. */
  Set<String> positions(final String fixture, final String id) {
    return found.getOrDefault(fixture + "|" + id, Set.of());
  }

  /** Marks {@code fixture} as not comparable, for every id of the tool. */
  void fixtureNotComparable(final String fixture, final String reason) {
    fixtureReasons.putIfAbsent(fixture, reason);
  }

  /** Marks {@code id} as not comparable, for every fixture. */
  void idNotComparable(final String id, final String reason) {
    idReasons.putIfAbsent(id, reason);
  }

  /** Returns why {@code id} cannot be compared on {@code fixture}, if it cannot. */
  Optional<String> reason(final String fixture, final String id) {
    return Optional.ofNullable(idReasons.getOrDefault(id, fixtureReasons.get(fixture)));
  }
}
