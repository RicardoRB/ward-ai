package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.DocNode.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The keywords whose appearance narrows a request or whose disappearance widens a response, and the
 * keywords whose value changing breaks both.
 */
final class KeywordChanges {

  private static final Predicate<DocNode> ANY = node -> true;
  private static final Predicate<DocNode> OBJECT = node -> node.kind() == Kind.OBJECT;
  private static final Predicate<DocNode> TRUE = node -> "true".equals(node.text());

  private static final List<Presence> PRESENCE =
      List.of(
          new Presence("multipleOf", "multiple-of-set", "multiple-of-unset", ANY),
          new Presence("uniqueItems", "unique-items-set", "unique-items-unset", TRUE),
          new Presence("pattern", "pattern-added", "pattern-removed", ANY),
          new Presence("enum", "became-enum", "became-not-enum", ANY),
          new Presence("const", "const-added", "const-removed", ANY),
          new Presence("contains", "contains-added", "contains-removed", ANY),
          new Presence("contentSchema", "content-schema-added", "content-schema-removed", ANY),
          new Presence(
              "dependentRequired", "dependent-required-added", "dependent-required-removed", ANY),
          new Presence(
              "dependentSchemas", "dependent-schema-added", "dependent-schema-removed", ANY),
          new Presence("if", "if-added", "if-removed", ANY),
          new Presence("then", "then-added", "then-removed", ANY),
          new Presence("else", "else-added", "else-removed", ANY),
          new Presence(
              "patternProperties", "pattern-property-added", "pattern-property-removed", ANY),
          new Presence("propertyNames", "property-names-added", "property-names-removed", ANY),
          new Presence(
              "unevaluatedItems", "unevaluated-items-added", "unevaluated-items-removed", OBJECT),
          new Presence(
              "unevaluatedProperties",
              "unevaluated-properties-added",
              "unevaluated-properties-removed",
              OBJECT));

  private static final List<Changed> CHANGED =
      List.of(
          new Changed("const", "const-changed"),
          new Changed("multipleOf", "multiple-of-changed"),
          new Changed("dependentRequired", "dependent-required-changed"),
          new Changed("contentEncoding", "content-encoding-changed"),
          new Changed("contentMediaType", "content-media-type-changed"));

  /** A keyword and the names of the checks for it appearing and for it disappearing. */
  private record Presence(
      String keyword, String added, String removed, Predicate<DocNode> counts) {}

  /** A keyword and the name of the check for its value changing. */
  private record Changed(String keyword, String kind) {}

  private KeywordChanges() {}

  /** The kinds this class reports in the given direction. */
  static List<String> kinds(final Direction direction) {
    final List<String> kinds = new ArrayList<>();
    for (final Presence presence : PRESENCE) {
      kinds.add(direction == Direction.REQUEST ? presence.added() : presence.removed());
    }
    CHANGED.forEach(changed -> kinds.add(changed.kind()));
    return kinds;
  }

  /** The keyword changes of one schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final Presence presence : PRESENCE) {
      presence(sides, base, revision, presence).ifPresent(changes::add);
    }
    for (final Changed changed : CHANGED) {
      changed(base, revision, changed).ifPresent(changes::add);
    }
    return changes;
  }

  private static Optional<SchemaChange> presence(
      final SchemaSides sides, final DocNode base, final DocNode revision, final Presence row) {
    final Optional<DocNode> before = base.get(row.keyword()).filter(row.counts());
    final Optional<DocNode> after = revision.get(row.keyword()).filter(row.counts());
    if (sides.request() && before.isEmpty()) {
      return after.map(node -> new SchemaChange(row.added(), node));
    }
    if (!sides.request() && after.isEmpty() && before.isPresent()) {
      return Optional.of(new SchemaChange(row.removed(), revision));
    }
    return Optional.empty();
  }

  private static Optional<SchemaChange> changed(
      final DocNode base, final DocNode revision, final Changed row) {
    final Optional<DocNode> before = base.get(row.keyword());
    final Optional<DocNode> after = revision.get(row.keyword());
    if (before.isPresent() && after.isPresent() && !SameNodes.equal(before.get(), after.get())) {
      return Optional.of(new SchemaChange(row.kind(), after.get()));
    }
    return Optional.empty();
  }
}
