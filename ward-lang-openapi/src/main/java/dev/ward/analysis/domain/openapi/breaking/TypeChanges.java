package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a schema accepts: its type, whether it is nullable, whether it became {@code false}, and the
 * values of its enum.
 */
final class TypeChanges {

  private static final String NULL = "null";
  private static final String INTEGER = "integer";
  private static final String NUMBER = "number";

  private TypeChanges() {}

  /** The kinds this class reports in the given direction. */
  static List<String> kinds(final Direction direction) {
    return direction == Direction.REQUEST
        ? List.of(
            "type-changed",
            "became-not-nullable",
            "schema-became-false",
            "enum-value-removed",
            "x-extensible-enum-value-removed")
        : List.of("type-changed", "type-generalized", "became-nullable", "schema-became-not-false");
  }

  /** The type changes of one schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    type(sides, base, revision).ifPresent(changes::add);
    nullable(sides, base, revision).ifPresent(changes::add);
    falseSchema(sides, base, revision).ifPresent(changes::add);
    return changes;
  }

  private static Optional<SchemaChange> type(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final Set<String> before = types(base);
    final Set<String> after = types(revision);
    if (before.isEmpty() || after.isEmpty()) {
      return Optional.empty();
    }
    final DocNode at = revision.get("type").orElse(revision);
    if (sides.request()) {
      return covers(after, before)
          ? Optional.empty()
          : Optional.of(new SchemaChange("type-changed", at));
    }
    if (covers(before, after)) {
      return Optional.empty();
    }
    final String kind = covers(after, before) ? "type-generalized" : "type-changed";
    return Optional.of(new SchemaChange(kind, at));
  }

  /** Tells whether every type of {@code inner} is accepted by one of {@code outer}. */
  private static boolean covers(final Set<String> outer, final Set<String> inner) {
    return inner.stream()
        .allMatch(type -> outer.contains(type) || (INTEGER.equals(type) && outer.contains(NUMBER)));
  }

  /** The types a schema names, without {@code null}. */
  private static Set<String> types(final DocNode schema) {
    final Set<String> types = new LinkedHashSet<>();
    schema
        .get("type")
        .ifPresent(
            node -> {
              if (node.isScalar()) {
                types.add(node.text());
              } else {
                node.items().forEach(item -> types.add(item.text()));
              }
            });
    types.remove(NULL);
    return types;
  }

  private static boolean nullable(final DocNode schema) {
    final boolean flagged =
        schema.get("nullable").filter(node -> "true".equals(node.text())).isPresent();
    final boolean listed =
        schema.get("type").filter(node -> !node.isScalar()).stream()
            .flatMap(node -> node.items().stream())
            .anyMatch(item -> NULL.equals(item.text()));
    return flagged || listed;
  }

  private static Optional<SchemaChange> nullable(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final boolean before = nullable(base);
    final boolean after = nullable(revision);
    if (sides.request() && before && !after) {
      return Optional.of(new SchemaChange("became-not-nullable", revision));
    }
    if (!sides.request() && !before && after) {
      return Optional.of(new SchemaChange("became-nullable", revision));
    }
    return Optional.empty();
  }

  private static Optional<SchemaChange> falseSchema(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final boolean before = isFalse(base);
    final boolean after = isFalse(revision);
    if (sides.request()) {
      return !before && after
          ? Optional.of(new SchemaChange("schema-became-false", revision))
          : Optional.empty();
    }
    if (before != after) {
      return Optional.of(
          new SchemaChange(before ? "schema-became-not-false" : "schema-became-false", revision));
    }
    return Optional.empty();
  }

  private static boolean isFalse(final DocNode schema) {
    return schema.isScalar() && "false".equals(schema.text());
  }
}
