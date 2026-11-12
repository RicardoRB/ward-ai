package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Compares a schema of the base with the same schema of the revision. */
final class SchemaDiff {

  private SchemaDiff() {}

  /** The changes of the schema itself, not of what lies below it. */
  static List<SchemaChange> root(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    changes.addAll(LimitChanges.changes(sides, base, revision));
    changes.addAll(KeywordChanges.changes(sides, base, revision));
    changes.addAll(TypeChanges.changes(sides, base, revision));
    changes.addAll(EnumChanges.changes(sides, base, revision));
    changes.addAll(HeaderTypeChanges.changes(sides, base, revision));
    changes.addAll(CompositionChanges.changes(sides, base, revision));
    return changes;
  }

  /** The changes of every schema below the root: its properties, items and extra properties. */
  static List<SchemaChange> below(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final Pair pair : nested(sides, base, revision)) {
      changes.addAll(root(sides, pair.base(), pair.revision()));
    }
    return changes;
  }

  /** The required-property changes of the root and of every schema below it. */
  static List<SchemaChange> required(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes =
        new ArrayList<>(RequiredChanges.changes(sides, base, revision));
    for (final Pair pair : nested(sides, base, revision)) {
      changes.addAll(RequiredChanges.changes(sides, pair.base(), pair.revision()));
    }
    return changes;
  }

  /** A schema of the base and the schema at the same place in the revision. */
  record Pair(DocNode base, DocNode revision) {}

  /** The pairs of schemas below the root: properties, items and extra properties. */
  static List<Pair> nested(final SchemaSides sides, final DocNode base, final DocNode revision) {
    final Set<DocNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<Pair> pairs = new ArrayList<>();
    collect(sides, base, revision, seen, pairs);
    return pairs;
  }

  private static void collect(
      final SchemaSides sides,
      final DocNode base,
      final DocNode revision,
      final Set<DocNode> seen,
      final List<Pair> pairs) {
    if (!seen.add(base)) {
      return;
    }
    for (final String key : List.of("items", "additionalProperties")) {
      add(sides, sides.before(base, key), sides.after(revision, key), seen, pairs);
    }
    for (final DocNode.Entry property :
        base.get("properties").map(DocNode::entries).orElse(List.of())) {
      add(
          sides,
          Optional.of(sides.base().resolve(property.value())),
          revision
              .get("properties")
              .flatMap(properties -> properties.get(property.key()))
              .map(sides.revision()::resolve),
          seen,
          pairs);
    }
  }

  private static void add(
      final SchemaSides sides,
      final Optional<DocNode> base,
      final Optional<DocNode> revision,
      final Set<DocNode> seen,
      final List<Pair> pairs) {
    if (base.isPresent() && revision.isPresent()) {
      pairs.add(new Pair(base.get(), revision.get()));
      collect(sides, base.get(), revision.get(), seen, pairs);
    }
  }
}
