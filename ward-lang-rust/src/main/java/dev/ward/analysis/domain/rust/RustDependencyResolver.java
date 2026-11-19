package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves Rust module paths to files. {@code crate::a::b} names module {@code b} of module {@code
 * a} of the importing crate, {@code self} and {@code super} start from the module of the importing
 * file, and the name of a workspace crate starts at that crate's root. A path whose last segments
 * name items resolves to the deepest module a file defines, so {@code use crate::a::Thing} is an
 * edge to the file of {@code a}. A path into a crate outside the project produces no edge.
 */
public final class RustDependencyResolver implements DependencyResolver {
  private static final String SEPARATOR = "::";
  private static final String CRATE = "crate";
  private static final String SELF = "self";
  private static final String SUPER = "super";

  private final RustProjectLayout layout;

  /**
   * Creates the resolver for a project's crates.
   *
   * @param layout the crates that map files to modules
   * @throws NullPointerException if the layout is missing
   */
  public RustDependencyResolver(final RustProjectLayout layout) {
    this.layout = Objects.requireNonNull(layout, "layout");
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final Map<String, String> files = new HashMap<>();
    for (final FileSummary summary : summaries) {
      layout
          .moduleOf(summary.path())
          .ifPresent(module -> files.merge(module.name(), summary.path(), RustDependencyResolver::smaller));
    }
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final FileSummary summary : summaries) {
      final Optional<RustModule> owner = layout.moduleOf(summary.path());
      for (final Reference reference : summary.imports()) {
        target(reference.specifier(), owner, files)
            .filter(target -> !target.equals(summary.path()))
            .ifPresent(target -> edges.add(new DependencyEdge(summary.path(), target, reference)));
      }
    }
    return edges;
  }

  /** Two files that claim one module, such as alternatives under {@code cfg}: the smaller path. */
  private static String smaller(final String existing, final String candidate) {
    return candidate.compareTo(existing) < 0 ? candidate : existing;
  }

  private Optional<String> target(
      final String specifier,
      final Optional<RustModule> owner,
      final Map<String, String> files
  ) {
    final List<String> segments = List.of(specifier.split(SEPARATOR));
    final String first = segments.getFirst();
    final Optional<RustModule> start =
        switch (first) {
          case CRATE -> owner.map(module -> new RustModule(module.crate(), List.of()));
          case SELF -> owner;
          case SUPER -> up(owner, segments);
          default -> layout.crate(first).map(crate -> new RustModule(crate.name(), List.of()));
        };
    return start.flatMap(module -> deepest(module, rest(first, segments), files));
  }

  /** The module after each leading {@code super}, or empty when it climbs above the root. */
  private static Optional<RustModule> up(
      final Optional<RustModule> owner,
      final List<String> segments
  ) {
    Optional<RustModule> module = owner;
    for (final String segment : segments) {
      if (!SUPER.equals(segment)) {
        break;
      }
      module = module.flatMap(RustModule::parent);
    }
    return module;
  }

  private static List<String> rest(final String first, final List<String> segments) {
    int skip = 1;
    if (SUPER.equals(first)) {
      while (skip < segments.size() && SUPER.equals(segments.get(skip))) {
        skip++;
      }
    }
    return segments.subList(skip, segments.size());
  }

  private static Optional<String> deepest(
      final RustModule start,
      final List<String> rest,
      final Map<String, String> files
  ) {
    for (int length = rest.size(); length >= 0; length--) {
      final String file = files.get(start.child(rest.subList(0, length)).name());
      if (file != null) {
        return Optional.of(file);
      }
    }
    return Optional.empty();
  }
}
