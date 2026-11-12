package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The values an enum lists: a request lost one, a response gained one. */
final class EnumChanges {

  private EnumChanges() {}

  /** The enum changes of one schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    enumValues(sides, base, revision).ifPresent(changes::add);
    extensibleEnum(sides, base, revision).ifPresent(changes::add);
    return changes;
  }

  private static Optional<SchemaChange> enumValues(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final Optional<DocNode> before = base.get("enum");
    final Optional<DocNode> after = revision.get("enum");
    if (before.isEmpty() || after.isEmpty()) {
      return Optional.empty();
    }
    if (sides.request() && lacks(after.get(), before.get())) {
      return Optional.of(new SchemaChange("enum-value-removed", after.get()));
    }
    if (!sides.request() && lacks(before.get(), after.get())) {
      return Optional.of(new SchemaChange("enum-value-added", after.get()));
    }
    return Optional.empty();
  }

  /** A value of {@code x-extensible-enum} that a request no longer lists. */
  private static Optional<SchemaChange> extensibleEnum(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final Optional<DocNode> before = base.get("x-extensible-enum");
    final Optional<DocNode> after = revision.get("x-extensible-enum");
    if (sides.request()
        && before.isPresent()
        && after.isPresent()
        && lacks(after.get(), before.get())) {
      return Optional.of(new SchemaChange("x-extensible-enum-value-removed", after.get()));
    }
    return Optional.empty();
  }

  /** Tells whether {@code outer} lacks a value of {@code inner}. */
  private static boolean lacks(final DocNode outer, final DocNode inner) {
    return inner.items().stream()
        .anyMatch(
            value -> outer.items().stream().noneMatch(other -> SameNodes.equal(value, other)));
  }
}
