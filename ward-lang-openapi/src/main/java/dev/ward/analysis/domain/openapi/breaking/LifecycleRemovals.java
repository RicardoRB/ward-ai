package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.breaking.SchemaSites.Site;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rules about removing a parameter or a property that was deprecated with a sunset date still
 * to come.
 */
final class LifecycleRemovals {

  private LifecycleRemovals() {}

  /** The removal rules. */
  static List<BreakingRule> rules() {
    return List.of(
        RowRule.of("request-parameter-removed-before-sunset", LifecycleRemovals::parameters),
        RowRule.of(
            "request-property-removed-before-sunset",
            pair -> properties(pair, SchemaSites.requestBodies(pair), Direction.REQUEST)),
        RowRule.of(
            "response-required-property-removed-before-sunset",
            pair -> properties(pair, SchemaSites.responseBodies(pair), Direction.RESPONSE)));
  }

  private static List<BreakingFinding> parameters(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final Map<String, DocNode> after = NamedParts.parameters(pair.revision(), now);
          final List<BreakingFinding> findings = new ArrayList<>();
          NamedParts.parameters(pair.base(), base)
              .forEach(
                  (key, before) -> {
                    if (!after.containsKey(key) && early(before, pair.today())) {
                      findings.add(
                          finding(
                              "request-parameter-removed-before-sunset",
                              now.node(),
                              OperationIndex.method(now),
                              now.path(),
                              "the parameter was removed before its sunset date"));
                    }
                  });
          return findings;
        });
  }

  private static List<BreakingFinding> properties(
      final DocumentPair pair, final List<Site> sites, final Direction direction) {
    final SchemaSides sides = new SchemaSides(pair.base(), pair.revision(), direction);
    final String id =
        direction == Direction.REQUEST
            ? "request-property-removed-before-sunset"
            : "response-required-property-removed-before-sunset";
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Site site : sites) {
      for (final SchemaDiff.Pair schemas : withRoot(sides, site)) {
        for (final DocNode.Entry property : removed(schemas, direction, pair.today())) {
          findings.add(
              finding(
                  id,
                  schemas.revision().get("properties").orElse(schemas.revision()),
                  OperationIndex.method(site.operation()),
                  site.operation().path(),
                  "the property " + property.key() + " was removed before its sunset date"));
        }
      }
    }
    return findings;
  }

  private static List<SchemaDiff.Pair> withRoot(final SchemaSides sides, final Site site) {
    final List<SchemaDiff.Pair> pairs =
        new ArrayList<>(SchemaDiff.nested(sides, site.base(), site.revision()));
    pairs.add(new SchemaDiff.Pair(site.base(), site.revision()));
    return pairs;
  }

  /** The properties of the base schema that the revision lacks and that were to end later. */
  private static List<DocNode.Entry> removed(
      final SchemaDiff.Pair schemas, final Direction direction, final LocalDate today) {
    final List<DocNode.Entry> removed = new ArrayList<>();
    for (final DocNode.Entry property :
        schemas.base().get("properties").map(DocNode::entries).orElse(List.of())) {
      final boolean gone =
          schemas.revision().get("properties").flatMap(kept -> kept.get(property.key())).isEmpty();
      if (gone && early(property.value(), today) && wanted(schemas.base(), property, direction)) {
        removed.add(property);
      }
    }
    return removed;
  }

  /** A response only cares about the properties it promised to send. */
  private static boolean wanted(
      final DocNode base, final DocNode.Entry property, final Direction direction) {
    return direction == Direction.REQUEST
        || base.get("required").stream()
            .flatMap(required -> required.items().stream())
            .anyMatch(item -> item.text().equals(property.key()));
  }

  /** Tells whether the node is deprecated with a sunset date after today. */
  private static boolean early(final DocNode node, final LocalDate today) {
    final Optional<LocalDate> day =
        Lifecycle.deprecated(node)
            ? Lifecycle.sunset(node).flatMap(Lifecycle::day)
            : Optional.empty();
    return day.filter(date -> date.isAfter(today)).isPresent();
  }

  private static BreakingFinding finding(
      final String id,
      final DocNode at,
      final String method,
      final String path,
      final String message) {
    return new BreakingFinding(
        RowRule.PREFIX + id, method + " " + path + ": " + message, method, path, at);
  }
}
