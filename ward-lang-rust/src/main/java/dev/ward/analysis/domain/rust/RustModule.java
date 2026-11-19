package dev.ward.analysis.domain.rust;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A module of a crate, named by the path from the crate root.
 *
 * @param crate the name of the crate
 * @param segments the module names below the crate root; empty for the root itself
 */
public record RustModule(String crate, List<String> segments) {
  private static final String SEPARATOR = "::";

  /** Copies the segments. */
  public RustModule {
    segments = List.copyOf(segments);
  }

  /** Returns the key of the module: the crate name, then each segment, joined by {@code ::}. */
  public String name() {
    return segments.isEmpty() ? crate : crate + SEPARATOR + String.join(SEPARATOR, segments);
  }

  /** Returns the module that holds this one, or empty for a crate root. */
  public Optional<RustModule> parent() {
    return segments.isEmpty()
        ? Optional.empty()
        : Optional.of(new RustModule(crate, segments.subList(0, segments.size() - 1)));
  }

  /** Returns the module {@code names} below this one. */
  public RustModule child(final List<String> names) {
    final List<String> all = new ArrayList<>(segments);
    all.addAll(names);
    return new RustModule(crate, all);
  }
}
