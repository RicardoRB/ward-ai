package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;

/** The read-only and write-only flags of the required properties of a schema. */
final class AccessChanges {

  private AccessChanges() {}

  /** The required properties of the revision that a request now has to send. */
  static List<SchemaChange> writable(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : RequiredChanges.required(revision)) {
      if (flag(base, name.text(), "readOnly") && !flag(revision, name.text(), "readOnly")) {
        changes.add(
            new SchemaChange("became-not-read-only", revision.get("properties").orElse(revision)));
      }
    }
    return changes;
  }

  /** The required properties of the revision that stopped being readable. */
  static List<SchemaChange> writeOnly(final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final DocNode name : RequiredChanges.required(revision)) {
      final boolean was = flag(base, name.text(), "writeOnly");
      if (!was && flag(revision, name.text(), "writeOnly")) {
        changes.add(
            new SchemaChange("became-write-only", revision.get("properties").orElse(revision)));
      }
    }
    return changes;
  }

  static boolean flag(final DocNode schema, final String property, final String flag) {
    return schema
        .get("properties")
        .flatMap(properties -> properties.get(property))
        .flatMap(node -> node.get(flag))
        .filter(node -> "true".equals(node.text()))
        .isPresent();
  }
}
