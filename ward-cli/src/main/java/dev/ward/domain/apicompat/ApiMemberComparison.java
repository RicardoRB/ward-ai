package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Compares the members of one type that is API in both versions. */
final class ApiMemberComparison {
  private static final Set<String> OBJECT_METHODS =
      Set.of(
          "toString()",
          "hashCode()",
          "equals(java.lang.Object)",
          "getClass()",
          "notify()",
          "notifyAll()",
          "wait()",
          "wait(long)",
          "wait(long,int)");

  private final ApiIndex before;
  private final ApiIndex after;
  private final List<ApiChange> changes;

  ApiMemberComparison(final ApiIndex before, final ApiIndex after, final List<ApiChange> changes) {
    this.before = before;
    this.after = after;
    this.changes = changes;
  }

  void compare(final ApiType old, final ApiType now) {
    for (final ApiMember member : old.members()) {
      if (old.exposes(member)) {
        compareMember(old, now, member);
      }
    }
    for (final ApiMember member : now.members()) {
      if (old.extendable() && now.exposes(member) && member.isAbstract()) {
        addedAbstract(old, member);
      }
    }
  }

  private void compareMember(final ApiType old, final ApiType now, final ApiMember member) {
    final Optional<ApiMember> found = ApiIndex.member(now, member.id());
    if (found.isEmpty()) {
      removed(old, now, member);
      return;
    }
    final String kind = ApiMemberKinds.of(old, member, found.get());
    if (kind != null) {
      changes.add(
          new ApiChange(
              kind,
              old.name(),
              member.name(),
              member.signature(),
              found.get().signature(),
              found.get().location()));
    }
  }

  private void removed(final ApiType old, final ApiType now, final ApiMember member) {
    if (movedUp(now, member) || inheritedFromObject(member)) {
      return;
    }
    changes.add(
        new ApiChange(
            "member-removed",
            old.name(),
            member.name(),
            member.signature(),
            ApiChange.REMOVED,
            now.location()));
  }

  private boolean movedUp(final ApiType now, final ApiMember member) {
    return member.kind() == ApiMember.Kind.METHOD && after.inheritsApiMember(now, member.id());
  }

  /** The methods of {@code Object} are inherited by every class, so overriding one is optional. */
  private static boolean inheritedFromObject(final ApiMember member) {
    return member.kind() == ApiMember.Kind.METHOD
        && !member.isStatic()
        && OBJECT_METHODS.contains(member.id());
  }

  /** A new abstract method breaks implementers, unless the baseline already had it. */
  private void addedAbstract(final ApiType old, final ApiMember member) {
    if (ApiIndex.member(old, member.id()).isPresent()
        || before.inheritsApiMember(old, member.id())) {
      return;
    }
    changes.add(
        new ApiChange(
            "abstract-method-added",
            old.name(),
            member.name(),
            "none",
            member.signature(),
            member.location()));
  }
}
