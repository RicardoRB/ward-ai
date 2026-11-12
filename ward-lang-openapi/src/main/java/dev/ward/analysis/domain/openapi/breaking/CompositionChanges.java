package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The alternatives of a schema: {@code oneOf} and {@code anyOf} members that went, allOf that came.
 */
final class CompositionChanges {

  private CompositionChanges() {}

  /** The composition changes of one schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    final boolean request = sides.request();
    final DocNode from = request ? base : revision;
    final DocNode other = request ? revision : base;
    final String list = request ? "list-of-types-narrowed" : "list-of-types-widened";
    final List<Member> members =
        request
            ? List.of(
                new Member("oneOf", "one-of-removed", list, from, other),
                new Member("anyOf", "any-of-removed", list, from, other),
                new Member("allOf", "all-of-added", "all-of-added", other, from))
            : List.of(
                new Member("oneOf", "one-of-added", list, from, other),
                new Member("anyOf", "any-of-added", list, from, other));
    for (final Member member : members) {
      member.change(revision).ifPresent(changes::add);
    }
    return changes;
  }

  /**
   * A list of {@code keyword} in which {@code from} has a member that {@code other} lacks.
   *
   * @param keyword the composition keyword
   * @param kind the check
   * @param listKind the check when both lists only name plain types
   * @param from the schema whose members are looked up
   * @param other the schema that should have them
   */
  private record Member(String keyword, String kind, String listKind, DocNode from, DocNode other) {

    Optional<SchemaChange> change(final DocNode revision) {
      final Optional<DocNode> members = from.get(keyword);
      final Optional<DocNode> remaining = other.get(keyword);
      if (members.isEmpty() || remaining.isEmpty() || !missing(members.get(), remaining.get())) {
        return Optional.empty();
      }
      final boolean plain = plain(members.get()) && plain(remaining.get());
      return Optional.of(
          new SchemaChange(plain ? listKind : kind, revision.get(keyword).orElse(revision)));
    }
  }

  /** Tells whether every member of a list is a schema that only names a type. */
  private static boolean plain(final DocNode members) {
    return members.items().stream()
        .allMatch(
            member ->
                member.entries().size() == 1
                    && member.get("type").filter(DocNode::isScalar).isPresent());
  }

  private static boolean missing(final DocNode members, final DocNode remaining) {
    return members.items().stream()
        .anyMatch(
            member ->
                remaining.items().stream().noneMatch(other -> SameNodes.equal(member, other)));
  }
}
