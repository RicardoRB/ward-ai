package dev.ward.analysis.application.rule;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The declarative checks of every provider, found by name. */
public final class CheckRegistry {

  private final Map<String, CheckFactory> factories;

  /**
   * Joins the checks of {@code providers}.
   *
   * @throws IllegalStateException if two providers register the same check name
   */
  public CheckRegistry(final Collection<? extends CheckProvider> providers) {
    final Map<String, CheckProvider> owners = new HashMap<>();
    final Map<String, CheckFactory> all = new HashMap<>();
    for (final CheckProvider provider : providers) {
      for (final Map.Entry<String, CheckFactory> entry : provider.checks().entrySet()) {
        final CheckProvider previous = owners.putIfAbsent(entry.getKey(), provider);
        if (previous != null) {
          throw new IllegalStateException(
              "check '"
                  + entry.getKey()
                  + "' is registered by both "
                  + previous.getClass().getSimpleName()
                  + " and "
                  + provider.getClass().getSimpleName());
        }
        all.put(entry.getKey(), entry.getValue());
      }
    }
    this.factories = Map.copyOf(all);
  }

  /**
   * Joins check maps of one provider.
   *
   * @throws IllegalStateException if two maps hold the same check name
   */
  public static Map<String, CheckFactory> union(final List<Map<String, CheckFactory>> parts) {
    final Map<String, CheckFactory> all = new HashMap<>();
    for (final Map<String, CheckFactory> part : parts) {
      for (final Map.Entry<String, CheckFactory> entry : part.entrySet()) {
        if (all.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
          throw new IllegalStateException("check '" + entry.getKey() + "' is registered twice");
        }
      }
    }
    return Map.copyOf(all);
  }

  /** Returns the factory of the check named {@code name}, if one is registered. */
  public Optional<CheckFactory> find(final String name) {
    return Optional.ofNullable(factories.get(name));
  }

  /** Returns the names of all registered checks. */
  public Set<String> names() {
    return factories.keySet();
  }
}
