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

  /** The kinds this class reports in the given direction. */
  static List<String> kinds(final Direction direction) {
    return direction == Direction.REQUEST
        ? List.of("one-of-removed", "any-of-removed", "all-of-added")
        : List.of("one-of-added", "any-of-added");
  }

  /** The composition changes of one schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    final boolean request = sides.request();
    final DocNode from = request ? base : revision;
    final DocNode other = request ? revision : base;
    final List<Member> members =
        request
            ? List.of(
                new Member("oneOf", "one-of-removed", from, other),
                new Member("anyOf", "any-of-removed", from, other),
                new Member("allOf", "all-of-added", other, from))
            : List.of(
                new Member("oneOf", "one-of-added", from, other),
                new Member("anyOf", "any-of-added", from, other));
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
   * @param from the schema whose members are looked up
   * @param other the schema that should have them
   */
  private record Member(String keyword, String kind, DocNode from, DocNode other) {

    Optional<SchemaChange> change(final DocNode revision) {
      final Optional<DocNode> members = from.get(keyword);
      final Optional<DocNode> remaining = other.get(keyword);
      if (members.isEmpty() || remaining.isEmpty() || !missing(members.get(), remaining.get())) {
        return Optional.empty();
      }
      return Optional.of(new SchemaChange(kind, revision.get(keyword).orElse(revision)));
    }
  }

  private static boolean missing(final DocNode members, final DocNode remaining) {
    return members.items().stream()
        .anyMatch(
            member ->
                remaining.items().stream().noneMatch(other -> SameNodes.equal(member, other)));
  }
}
