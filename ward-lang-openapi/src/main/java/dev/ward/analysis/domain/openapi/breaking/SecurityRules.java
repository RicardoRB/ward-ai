package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The rules about who may call an operation: the security requirements it ends up with, global or
 * its own, and the security schemes they name.
 */
final class SecurityRules {

  private static final String SECURITY = "security";

  private SecurityRules() {}

  /** The security rules. */
  static List<BreakingRule> rules() {
    return List.of(
        operations("api-security-removed", SecurityRules::removed),
        operations("api-security-scope-added", SecurityRules::scopeAdded),
        operations("api-security-anonymous-access-removed", SecurityRules::anonymousRemoved));
  }

  /** The security requirements an operation ends up with, where they are written. */
  private record Effective(Optional<DocNode> node, List<DocNode> alternatives) {

    boolean anonymous() {
      return alternatives.isEmpty()
          || alternatives.stream().anyMatch(alternative -> alternative.entries().isEmpty());
    }
  }

  private static Effective effective(final OpenApiDocument document, final Operation operation) {
    final Optional<DocNode> own = operation.node().get(SECURITY).map(document::resolve);
    final Optional<DocNode> node = own.isPresent() ? own : document.root().get(SECURITY);
    return new Effective(node, node.map(DocNode::items).orElse(List.of()));
  }

  private static BreakingRule operations(
      final String id, final BiFunction<Effective, Effective, Optional<String>> check) {
    return RowRule.of(
        id,
        pair ->
            OperationPairs.each(
                pair,
                (base, now) -> {
                  final Effective before = effective(pair.base(), base);
                  final Effective after = effective(pair.revision(), now);
                  return check
                      .apply(before, after)
                      .map(
                          message ->
                              new BreakingFinding(
                                  RowRule.PREFIX + id,
                                  OperationIndex.method(now) + " " + now.path() + ": " + message,
                                  OperationIndex.method(now),
                                  now.path(),
                                  after.node().orElse(now.node())))
                      .stream()
                      .toList();
                }));
  }

  /** The schemes a requirement names, sorted, as its identity. */
  private static String key(final DocNode alternative) {
    return alternative.entries().stream()
        .map(DocNode.Entry::key)
        .sorted()
        .collect(Collectors.joining("+"));
  }

  private static Optional<DocNode> alternative(final Effective effective, final String key) {
    return effective.alternatives().stream()
        .filter(candidate -> !candidate.entries().isEmpty() && key(candidate).equals(key))
        .findFirst();
  }

  private static Optional<String> removed(final Effective before, final Effective after) {
    if (after.alternatives().isEmpty()) {
      return Optional.empty();
    }
    return before.alternatives().stream()
        .filter(alternative -> !alternative.entries().isEmpty())
        .filter(alternative -> alternative(after, key(alternative)).isEmpty())
        .findFirst()
        .map(alternative -> "the security requirement " + key(alternative) + " was removed");
  }

  private static Optional<String> scopeAdded(final Effective before, final Effective after) {
    for (final DocNode alternative : before.alternatives()) {
      final Optional<DocNode> now = alternative(after, key(alternative));
      if (now.isPresent() && addsScope(alternative, now.get())) {
        return Optional.of("a scope was added to the security requirement " + key(alternative));
      }
    }
    return Optional.empty();
  }

  private static boolean addsScope(final DocNode before, final DocNode after) {
    return after.entries().stream()
        .anyMatch(
            entry ->
                entry.value().items().stream()
                    .anyMatch(
                        scope ->
                            before
                                .get(entry.key())
                                .filter(
                                    old ->
                                        old.items().stream()
                                            .anyMatch(item -> item.text().equals(scope.text())))
                                .isEmpty()));
  }

  private static Optional<String> anonymousRemoved(final Effective before, final Effective after) {
    return before.anonymous() && !after.anonymous()
        ? Optional.of("unauthenticated requests are no longer accepted")
        : Optional.empty();
  }
}
