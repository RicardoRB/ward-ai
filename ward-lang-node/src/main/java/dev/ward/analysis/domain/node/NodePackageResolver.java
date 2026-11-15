package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.node.NodeProjectLayout.ExportEntry;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodePackage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves a bare specifier that names a workspace package, through its manifest and exports. */
final class NodePackageResolver {
  private static final String SEPARATOR = "/";
  private static final String SCOPE = "@";
  private static final String WILDCARD = "*";
  private static final String TYPES = "types";
  private static final String DOT = ".";
  private static final String IMPORT = "import";
  private static final String REQUIRE = "require";
  private static final String DEFAULT = "default";
  private static final Set<String> IMPORT_CONDITIONS = Set.of(TYPES, IMPORT, DEFAULT);
  private static final Set<String> REQUIRE_CONDITIONS = Set.of(TYPES, REQUIRE, DEFAULT);

  private final NodeProjectLayout layout;

  /** Creates the resolver of workspace packages over {@code layout}. */
  NodePackageResolver(final NodeProjectLayout layout) {
    this.layout = layout;
  }

  /** Resolves {@code reference} to a file of a workspace package, or returns empty. */
  Optional<String> resolve(final NodeReference reference) {
    return viaPackage(reference);
  }

  /** Resolves a bare specifier that names a workspace package, through its manifest. */
  private Optional<String> viaPackage(final NodeReference reference) {
    final String specifier = reference.specifier();
    final int nameSegments = specifier.startsWith(SCOPE) ? 2 : 1;
    final String[] parts = specifier.split(SEPARATOR, nameSegments + 1);
    if (parts.length < nameSegments) {
      return Optional.empty();
    }
    final String name = String.join(SEPARATOR, List.of(parts).subList(0, nameSegments));
    final String rest = parts.length > nameSegments ? parts[nameSegments] : "";
    return layout.packages().stream()
        .filter(pkg -> pkg.name().equals(name))
        .findFirst()
        .flatMap(pkg -> resolvePackage(pkg, rest, reference));
  }

  private Optional<String> resolvePackage(
      final NodePackage pkg, final String rest, final NodeReference reference) {
    if (!pkg.exports().isEmpty()) {
      return viaExports(pkg, rest, reference);
    }
    if (!rest.isEmpty()) {
      return NodePaths.join(pkg.directory(), rest)
          .flatMap(base -> NodePaths.file(layout.files(), base));
    }
    for (final String field :
        List.of(nullToEmpty(pkg.types()), nullToEmpty(pkg.module()), nullToEmpty(pkg.main()))) {
      if (!field.isEmpty()) {
        final Optional<String> found =
            NodePaths.join(pkg.directory(), field)
                .flatMap(base -> NodePaths.file(layout.files(), base));
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return NodePaths.join(pkg.directory(), "index")
        .flatMap(base -> NodePaths.file(layout.files(), base));
  }

  private Optional<String> viaExports(
      final NodePackage pkg, final String rest, final NodeReference reference) {
    final String subpath = rest.isEmpty() ? DOT : DOT + SEPARATOR + rest;
    final Set<String> active = reference.isRequire() ? REQUIRE_CONDITIONS : IMPORT_CONDITIONS;
    final Optional<Entries> entries = entriesFor(pkg.exports(), subpath);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    for (final ExportEntry entry : entries.get().entries()) {
      if (active.containsAll(entry.conditions())) {
        final String target = entry.target().replace(WILDCARD, entries.get().capture());
        return NodePaths.join(pkg.directory(), target).filter(layout.files()::contains);
      }
    }
    return Optional.empty();
  }

  /** The entries of an exports subpath and the text a pattern captured for it. */
  private record Entries(List<ExportEntry> entries, String capture) {}

  /** Returns the exact subpath's entries, or those of the pattern with the longest prefix. */
  private static Optional<Entries> entriesFor(
      final Map<String, List<ExportEntry>> exports, final String subpath) {
    if (exports.containsKey(subpath)) {
      return Optional.of(new Entries(exports.get(subpath), ""));
    }
    return longestPattern(exports, subpath);
  }

  private static Optional<Entries> longestPattern(
      final Map<String, List<ExportEntry>> exports, final String subpath) {
    String bestKey = null;
    String bestCapture = null;
    for (final String key : exports.keySet()) {
      final Optional<String> capture = NodeSpecifiers.capture(key, subpath);
      if (capture.isPresent() && moreSpecific(key, bestKey)) {
        bestKey = key;
        bestCapture = capture.get();
      }
    }
    return bestKey == null
        ? Optional.empty()
        : Optional.of(new Entries(exports.get(bestKey), bestCapture));
  }

  private static boolean moreSpecific(final String key, final String bestKey) {
    return bestKey == null || key.indexOf(WILDCARD) > bestKey.indexOf(WILDCARD);
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}
