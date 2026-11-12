package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.breaking.SchemaSites.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The rules that compare a schema of the base with the same schema of the revision, one rule per
 * place a schema can be (request body, request property, parameter, response body, response
 * property, response header) and kind of change.
 */
final class SchemaRules {

  private static final List<Irregular> IRREGULAR =
      List.of(
          new Irregular("new-required-request-property", Place.REQUEST_PROPERTY, "new-required"),
          new Irregular(
              "request-required-property-became-not-read-only",
              Place.REQUEST_PROPERTY,
              "became-not-read-only"),
          new Irregular(
              "new-required-request-property-with-default",
              Place.REQUEST_PROPERTY,
              "new-required-with-default"),
          new Irregular(
              "response-required-property-removed", Place.RESPONSE_PROPERTY, "required-removed"),
          new Irregular(
              "response-mediatype-became-not-enum", Place.RESPONSE_BODY, "became-not-enum"),
          new Irregular(
              "response-mediatype-enum-value-added", Place.RESPONSE_BODY, "enum-value-added"),
          new Irregular(
              "response-required-property-became-write-only",
              Place.RESPONSE_PROPERTY,
              "became-write-only"),
          new Irregular(
              "request-parameter-property-type-specialized",
              Place.REQUEST_PARAMETER_PROPERTY,
              "type-changed"),
          new Irregular(
              "new-required-request-header-property",
              Place.REQUEST_HEADER_PROPERTY,
              "new-required"));

  /** Where the checks with an id of their own report: the place and the kind of change. */
  private record Irregular(String id, Place place, String kind) {}

  /** How much of a schema a place looks at. */
  private enum Depth {
    /** The schema itself. */
    ROOT,
    /** The schemas below it. */
    BELOW,
    /** Both. */
    BOTH
  }

  /** A place of schemas: the id prefix, how to find them and whether it looks below the root. */
  private enum Place {
    REQUEST_BODY("request-body", Direction.REQUEST, SchemaSites::requestBodies, Depth.ROOT),
    REQUEST_PROPERTY(
        "request-property", Direction.REQUEST, SchemaSites::requestBodies, Depth.BELOW),
    REQUEST_PARAMETER(
        "request-parameter",
        Direction.REQUEST,
        pair -> SchemaSites.parameters(pair, false),
        Depth.ROOT),
    REQUEST_PARAMETER_PROPERTY(
        "request-parameter-property",
        Direction.REQUEST,
        pair -> SchemaSites.parameters(pair, false),
        Depth.BELOW),
    REQUEST_HEADER_PROPERTY(
        "request-header-property",
        Direction.REQUEST,
        pair -> SchemaSites.parameters(pair, true),
        Depth.BOTH),
    RESPONSE_BODY("response-body", Direction.RESPONSE, SchemaSites::responseBodies, Depth.ROOT),
    RESPONSE_PROPERTY(
        "response-property", Direction.RESPONSE, SchemaSites::responseBodies, Depth.BELOW),
    RESPONSE_HEADER("response-header", Direction.RESPONSE, SchemaSites::headers, Depth.ROOT);

    private final String prefix;
    private final Direction direction;
    private final Function<DocumentPair, List<Site>> sites;
    private final Depth depth;

    Place(
        final String prefix,
        final Direction direction,
        final Function<DocumentPair, List<Site>> sites,
        final Depth depth) {
      this.prefix = prefix;
      this.direction = direction;
      this.sites = sites;
      this.depth = depth;
    }
  }

  private SchemaRules() {}

  /** The schema rules. */
  static List<BreakingRule> rules() {
    final List<BreakingRule> rules = new ArrayList<>();
    for (final Place place : Place.values()) {
      for (final String kind : SchemaKinds.of(place.prefix)) {
        rules.add(RowRule.of(place.prefix + "-" + kind, pair -> findings(pair, place, kind)));
      }
    }
    for (final Irregular irregular : IRREGULAR) {
      rules.add(
          RowRule.of(
              irregular.id(),
              pair -> findings(pair, irregular.place(), irregular.kind(), irregular.id())));
    }
    return rules;
  }

  private static List<BreakingFinding> findings(
      final DocumentPair pair, final Place place, final String kind) {
    return findings(pair, place, kind, place.prefix + "-" + kind);
  }

  private static List<BreakingFinding> findings(
      final DocumentPair pair, final Place place, final String kind, final String id) {
    final SchemaSides sides = new SchemaSides(pair.base(), pair.revision(), place.direction);
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Site site : place.sites.apply(pair)) {
      for (final SchemaChange change : changes(sides, place, site)) {
        if (change.kind().equals(kind)) {
          findings.add(finding(id, site, change));
        }
      }
    }
    return findings;
  }

  private static List<SchemaChange> changes(
      final SchemaSides sides, final Place place, final Site site) {
    final List<SchemaChange> changes = new ArrayList<>();
    if (place.depth != Depth.BELOW) {
      changes.addAll(SchemaDiff.root(sides, site.base(), site.revision()));
    }
    if (place.depth != Depth.ROOT) {
      changes.addAll(SchemaDiff.below(sides, site.base(), site.revision()));
      changes.addAll(SchemaDiff.required(sides, site.base(), site.revision()));
    }
    return changes;
  }

  private static BreakingFinding finding(
      final String id, final Site site, final SchemaChange change) {
    final DocNode at = change.at();
    return new BreakingFinding(
        RowRule.PREFIX + id,
        OperationIndex.method(site.operation())
            + " "
            + site.operation().path()
            + ": "
            + change.kind().replace('-', ' '),
        OperationIndex.method(site.operation()),
        site.operation().path(),
        at);
  }
}
