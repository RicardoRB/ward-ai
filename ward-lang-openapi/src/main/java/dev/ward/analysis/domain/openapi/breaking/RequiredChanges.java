package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;

/** The required properties of an object schema: new ones in a request, lost ones in a response. */
final class RequiredChanges {

  private RequiredChanges() {}

  /** The kinds this class reports in the given direction. */
  static List<String> kinds(final Direction direction) {
    return direction == Direction.REQUEST
        ? List.of("became-required", "new-required")
        : List.of("became-optional", "required-removed");
  }

  /** The required-property changes of one object schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    return sides.request() ? added(base, revision) : lost(base, revision);
  }

  /** The properties the revision requires and the base did not. */
  private static List<SchemaChange> added(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : required(revision)) {
      if (!listed(base, name.text())) {
        final String kind = hasProperty(base, name.text()) ? "became-required" : "new-required";
        changes.add(new SchemaChange(kind, name));
      }
    }
    return changes;
  }

  /** The properties the base required and the revision does not. */
  private static List<SchemaChange> lost(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : required(base)) {
      if (!listed(revision, name.text())) {
        final String kind =
            hasProperty(revision, name.text()) ? "became-optional" : "required-removed";
        changes.add(new SchemaChange(kind, revision));
      }
    }
    return changes;
  }

  private static List<DocNode> required(final DocNode schema) {
    return schema.get("required").map(DocNode::items).orElse(List.of());
  }

  private static boolean listed(final DocNode schema, final String name) {
    return required(schema).stream().anyMatch(item -> item.text().equals(name));
  }

  private static boolean hasProperty(final DocNode schema, final String name) {
    return schema.get("properties").flatMap(properties -> properties.get(name)).isPresent();
  }
}
