package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.node.NodeProjectLayout.NodeTsConfig;
import java.util.Optional;
import java.util.Set;

/**
 * Turns one module specifier into a project file: relative paths, then the {@code paths} and {@code
 * baseUrl} of the nearest TypeScript config, then workspace packages by their manifest.
 */
final class NodeSpecifiers {
  private static final String SEPARATOR = "/";
  private static final String NODE_PREFIX = "node:";
  private static final String DOT = ".";
  private static final String WILDCARD = "*";
  private static final Set<String> BUILT_INS =
      Set.of(
          "assert",
          "async_hooks",
          "buffer",
          "child_process",
          "cluster",
          "console",
          "constants",
          "crypto",
          "dgram",
          "diagnostics_channel",
          "dns",
          "domain",
          "events",
          "fs",
          "http",
          "http2",
          "https",
          "inspector",
          "module",
          "net",
          "os",
          "path",
          "perf_hooks",
          "process",
          "punycode",
          "querystring",
          "readline",
          "repl",
          "stream",
          "string_decoder",
          "sys",
          "timers",
          "tls",
          "trace_events",
          "tty",
          "url",
          "util",
          "v8",
          "vm",
          "wasi",
          "worker_threads",
          "zlib");

  private final NodeProjectLayout layout;
  private final NodePackageResolver packages;

  /** Creates the resolver of specifiers over {@code layout}. */
  NodeSpecifiers(final NodeProjectLayout layout) {
    this.layout = layout;
    this.packages = new NodePackageResolver(layout);
  }

  /** Resolves {@code reference} written in the file {@code from}, or returns empty. */
  Optional<String> resolve(final String from, final NodeReference reference) {
    final String specifier = reference.specifier();
    if (isRelative(specifier)) {
      return NodePaths.join(NodePaths.parent(from), specifier)
          .flatMap(base -> NodePaths.file(layout.files(), base));
    }
    if (specifier.startsWith(NODE_PREFIX) || BUILT_INS.contains(firstSegment(specifier))) {
      return Optional.empty();
    }
    final Optional<String> aliased = viaTsConfig(from, specifier);
    return aliased.isPresent() ? aliased : packages.resolve(reference);
  }

  private static boolean isRelative(final String specifier) {
    return specifier.startsWith("./")
        || specifier.startsWith("../")
        || DOT.equals(specifier)
        || "..".equals(specifier);
  }

  private static String firstSegment(final String specifier) {
    final int slash = specifier.indexOf('/');
    return slash < 0 ? specifier : specifier.substring(0, slash);
  }

  /** Applies {@code paths} of the nearest config, then {@code baseUrl}. */
  private Optional<String> viaTsConfig(final String from, final String specifier) {
    final Optional<NodeTsConfig> config = nearest(NodePaths.parent(from));
    if (config.isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> mapped = viaPaths(config.get(), specifier);
    if (mapped.isPresent() || config.get().baseUrl() == null) {
      return mapped;
    }
    return NodePaths.join(config.get().baseUrl(), specifier)
        .flatMap(base -> NodePaths.file(layout.files(), base));
  }

  private Optional<String> viaPaths(final NodeTsConfig config, final String specifier) {
    String bestPattern = null;
    String bestCapture = null;
    int bestScore = -1;
    for (final String pattern : config.paths().keySet()) {
      final Optional<String> capture = capture(pattern, specifier);
      final int score =
          pattern.indexOf(WILDCARD) < 0 ? Integer.MAX_VALUE : pattern.indexOf(WILDCARD);
      if (capture.isPresent() && score > bestScore) {
        bestPattern = pattern;
        bestCapture = capture.get();
        bestScore = score;
      }
    }
    if (bestPattern == null || config.pathsBase() == null) {
      return Optional.empty();
    }
    for (final String target : config.paths().get(bestPattern)) {
      final Optional<String> found =
          NodePaths.join(config.pathsBase(), target.replace(WILDCARD, bestCapture))
              .flatMap(base -> NodePaths.file(layout.files(), base));
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** Returns the captured text of a pattern that matches the specifier, or empty if none does. */
  static Optional<String> capture(final String pattern, final String specifier) {
    final int star = pattern.indexOf(WILDCARD);
    if (star < 0) {
      return pattern.equals(specifier) ? Optional.of("") : Optional.empty();
    }
    final String prefix = pattern.substring(0, star);
    final String suffix = pattern.substring(star + 1);
    final boolean matches =
        specifier.length() >= prefix.length() + suffix.length()
            && specifier.startsWith(prefix)
            && specifier.endsWith(suffix);
    return matches
        ? Optional.of(specifier.substring(prefix.length(), specifier.length() - suffix.length()))
        : Optional.empty();
  }

  /** Returns the config whose directory is the longest one that contains {@code directory}. */
  private Optional<NodeTsConfig> nearest(final String directory) {
    NodeTsConfig best = null;
    for (final NodeTsConfig config : layout.tsconfigs()) {
      if (contains(config.directory(), directory)
          && (best == null || config.directory().length() > best.directory().length())) {
        best = config;
      }
    }
    return Optional.ofNullable(best);
  }

  private static boolean contains(final String outer, final String inner) {
    return outer.isEmpty() || outer.equals(inner) || inner.startsWith(outer + SEPARATOR);
  }
}
