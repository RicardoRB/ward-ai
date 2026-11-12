package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;

/** The required properties of an object schema: new ones in a request, lost ones in a response. */
final class RequiredChanges {

  private RequiredChanges() {}

  /** The required-property changes of one object schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    if (!sides.request()) {
      return lost(base, revision);
    }
    final List<SchemaChange> changes = added(base, revision);
    if (!sides.revision().isVersion31()) {
      changes.addAll(AccessChanges.writable(base, revision));
    }
    return changes;
  }

  /** The properties the revision requires and the base did not. */
  private static List<SchemaChange> added(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : required(revision)) {
      if (!listed(base, name.text())) {
        final boolean existed = hasProperty(base, name.text());
        final String suffix = hasDefault(revision, name.text()) ? "-with-default" : "";
        changes.add(
            new SchemaChange((existed ? "became-required" : "new-required") + suffix, name));
      }
    }
    return changes;
  }

  /** The properties the base required and the revision does not. */
  private static List<SchemaChange> lost(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : required(base)) {
      if (!listed(revision, name.text())) {
        final boolean kept = hasProperty(revision, name.text());
        if (kept) {
          changes.add(new SchemaChange("became-optional", revision));
        } else if (!deprecated(base, name.text())) {
          changes.add(new SchemaChange("required-removed", revision));
        }
      }
    }
    changes.addAll(AccessChanges.writeOnly(base, revision));
    return changes;
  }

  static List<DocNode> required(final DocNode schema) {
    return schema.get("required").map(DocNode::items).orElse(List.of());
  }

  private static boolean listed(final DocNode schema, final String name) {
    return required(schema).stream().anyMatch(item -> item.text().equals(name));
  }

  /** A property removed after being deprecated is the sunset rules' business, not this one's. */
  private static boolean deprecated(final DocNode schema, final String name) {
    return schema
        .get("properties")
        .flatMap(properties -> properties.get(name))
        .filter(Lifecycle::deprecated)
        .isPresent();
  }

  private static boolean hasDefault(final DocNode schema, final String name) {
    return schema
        .get("properties")
        .flatMap(properties -> properties.get(name))
        .flatMap(property -> property.get("default"))
        .isPresent();
  }

  private static boolean hasProperty(final DocNode schema, final String name) {
    return schema.get("properties").flatMap(properties -> properties.get(name)).isPresent();
  }
}
