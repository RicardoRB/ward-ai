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
      changes.addAll(writable(base, revision));
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
        final String kind =
            hasProperty(revision, name.text()) ? "became-optional" : "required-removed";
        changes.add(new SchemaChange(kind, revision));
      }
    }
    changes.addAll(writeOnly(base, revision));
    return changes;
  }

  /** The required properties of the revision that a request now has to send. */
  private static List<SchemaChange> writable(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : required(revision)) {
      if (flag(base, name.text(), "readOnly") && !flag(revision, name.text(), "readOnly")) {
        changes.add(
            new SchemaChange("became-not-read-only", revision.get("properties").orElse(revision)));
      }
    }
    return changes;
  }

  /** The required properties of the revision that stopped being readable. */
  private static List<SchemaChange> writeOnly(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : required(revision)) {
      final boolean was = flag(base, name.text(), "writeOnly");
      if (!was && flag(revision, name.text(), "writeOnly")) {
        changes.add(
            new SchemaChange("became-write-only", revision.get("properties").orElse(revision)));
      }
    }
    return changes;
  }

  private static boolean flag(final DocNode schema, final String property, final String flag) {
    return schema
        .get("properties")
        .flatMap(properties -> properties.get(property))
        .flatMap(node -> node.get(flag))
        .filter(node -> "true".equals(node.text()))
        .isPresent();
  }

  private static List<DocNode> required(final DocNode schema) {
    return schema.get("required").map(DocNode::items).orElse(List.of());
  }

  private static boolean listed(final DocNode schema, final String name) {
    return required(schema).stream().anyMatch(item -> item.text().equals(name));
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
