package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Marks a function whose result lists an entry of a directory only for a key that is the start of
 * the name of an entry, for the analysis of its callers. The function has one parameter {@code p}
 * and returns only {@code cache[p]}, where {@code cache} is an object that starts empty at the top
 * of the file and that only this function mentions. It writes {@code cache[p] = { list, ... }}
 * with {@code list} a local array that only grows through {@code list.push(...)} inside
 * {@code if (entry.startsWith(`${p}...`))}, in {@code for (const entry of entries)} over {@code
 * const entries = readdirSync(...)}. A directory entry holds no path separator, so when the list
 * is not empty, {@code p} holds none either. The site is named {@value #PREFIX_LISTING}: its
 * qualifier is {@code p} and its annotation the name of the list.
 *
 * <p>shortcut: a caller that changes the returned list is not followed, and only the synchronous
 * {@code readdirSync} is read; upgrade when a project fills such a list from its callers.
 */
final class NodeListings {
  /** The name of the site that marks such a function. */
  static final String PREFIX_LISTING = "<prefix-listing>";

  private static final Set<String> ENDS = Set.of(";", "}", "");

  private final ControlFlowBuilder flow;
  private final Map<String, List<Integer>> caches;

  NodeListings(final ControlFlowBuilder flow, final Map<String, List<Integer>> caches) {
    this.flow = flow;
    this.caches = Map.copyOf(caches);
  }

  /** Records the mark in the entry block when the function is such a function. */
  void mark(final SyntaxNode function, final List<String> parameters) {
    if (function == null || parameters.size() != 1 || caches.isEmpty()) {
      return;
    }
    final SyntaxNode body = NodeFlowSyntax.body(function);
    if (body == null) {
      return;
    }
    final NodeTokens tokens = new NodeTokens(NodeLeaves.of(body));
    list(tokens, parameters.get(0), function.span())
        .ifPresent(
            name -> {
              final int last = flow.current();
              flow.moveTo(0);
              flow.record(List.of(), List.of(site(parameters.get(0), name, function.span())));
              flow.moveTo(last);
            }
        );
  }

  /** The name of the list that the function fills and caches, when every rule holds. */
  private Optional<String> list(final NodeTokens tokens, final String key, final Span function) {
    final Optional<String> cache = cacheOf(tokens, key, function);
    final Optional<List<NodeListingLoop.Range>> frames = NodeListingLoop.frames(tokens, key);
    if (cache.isEmpty() || frames.isEmpty() || !returnsOnlyCache(tokens, cache.get(), key)) {
      return Optional.empty();
    }
    final Optional<NodeListingCache.Written> written =
        NodeListingCache.written(tokens, cache.get(), key);
    if (written.isEmpty() || written.get().members().isEmpty()) {
      return Optional.empty();
    }
    final String list = written.get().members().get(0);
    return NodeListingList.filled(tokens, list, frames.get(), written.get().places())
        ? Optional.of(list)
        : Optional.empty();
  }

  /** The object the function indexes with the key, when only this function mentions it. */
  private Optional<String> cacheOf(final NodeTokens tokens, final String key, final Span function) {
    for (int index = 0; index < tokens.size(); index++) {
      final String name = tokens.text(index);
      if (caches.containsKey(name) && tokens.match(index, name, "[", key, "]").isPresent()) {
        final boolean closed =
            caches.get(name).stream()
                .allMatch(
                    offset -> offset >= function.startOffset() && offset < function.endOffset()
                );
        return closed ? Optional.of(name) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** Whether every {@code return} of the body is {@code return cache[key]}. */
  private static boolean returnsOnlyCache(
      final NodeTokens tokens,
      final String cache,
      final String key
  ) {
    for (int index = 0; index < tokens.size(); index++) {
      if (tokens.isKeyword(index, "return")
          && !(tokens.match(index + 1, cache, "[", key, "]").isPresent()
              && ENDS.contains(tokens.text(index + NodeListingCache.INDEXED + 1)))) {
        return false;
      }
    }
    return true;
  }

  private static FlowSite site(final String key, final String list, final Span span) {
    return new FlowSite(
        FlowSiteKind.CALL,
        PREFIX_LISTING,
        key,
        Optional.empty(),
        List.of(list),
        List.of(),
        span
    );
  }
}
