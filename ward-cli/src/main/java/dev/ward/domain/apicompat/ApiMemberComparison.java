package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Compares the members of one type that is API in both versions. */
final class ApiMemberComparison {
  private static final String EMPTY = "";
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
        addedAbstract(old, now, member);
      }
    }
  }

  private void compareMember(final ApiType old, final ApiType now, final ApiMember member) {
    final Optional<ApiMember> found = ApiIndex.member(now, member.id());
    if (found.isEmpty()) {
      removed(old, now, member);
      return;
    }
    final ApiMember current = found.get();
    if (current.visibility().lessVisibleThan(member.visibility())) {
      change("member-less-visible", old, member, current, current.location());
    } else if (current.isStatic() != member.isStatic()) {
      change("static-changed", old, member, current, current.location());
    } else {
      compareTypes(old, member, current);
      compareInheritance(old, member, current);
    }
  }

  private void removed(final ApiType old, final ApiType now, final ApiMember member) {
    if (movedUp(now, member) || hiddenFromTypeBySuper(member)) {
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
  private static boolean hiddenFromTypeBySuper(final ApiMember member) {
    return member.kind() == ApiMember.Kind.METHOD
        && !member.isStatic()
        && OBJECT_METHODS.contains(member.id());
  }

  private void compareTypes(final ApiType old, final ApiMember member, final ApiMember current) {
    final boolean method = member.kind() != ApiMember.Kind.FIELD;
    if (!member.type().key().equals(current.type().key())) {
      change(
          method ? "return-type-changed" : "field-type-changed",
          old,
          member,
          current,
          current.location());
    } else if (method && signatureChanged(member, current)) {
      change("signature-changed", old, member, current, current.location());
    }
  }

  private static boolean signatureChanged(final ApiMember member, final ApiMember current) {
    return !ApiSignatures.parameterKeys(member.parameters())
            .equals(ApiSignatures.parameterKeys(current.parameters()))
        || member.isVarargs() != current.isVarargs()
        || boundsDiffer(member.typeParameters(), current.typeParameters());
  }

  private static boolean boundsDiffer(
      final List<ApiTypeParameter> before, final List<ApiTypeParameter> after) {
    return !bounds(before).equals(bounds(after));
  }

  private static List<String> bounds(final List<ApiTypeParameter> parameters) {
    return parameters.stream()
        .map(
            parameter ->
                parameter.bounds().stream().map(ApiTypeRef::key).collect(Collectors.joining("&")))
        .toList();
  }

  private void compareInheritance(
      final ApiType old, final ApiMember member, final ApiMember current) {
    final boolean method = member.kind() == ApiMember.Kind.METHOD;
    if (member.kind() == ApiMember.Kind.FIELD && !member.isFinal() && current.isFinal()) {
      change("field-now-final", old, member, current, current.location());
    } else if (method && old.extendable() && !member.isAbstract() && current.isAbstract()) {
      change("method-now-abstract", old, member, current, current.location());
    } else if (method
        && old.extendable()
        && !member.isFinal()
        && !member.isStatic()
        && current.isFinal()) {
      change("method-now-final", old, member, current, current.location());
    }
  }

  /**
   * A new abstract method breaks implementers, unless the baseline already had it by inheritance.
   */
  private void addedAbstract(final ApiType old, final ApiType now, final ApiMember member) {
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

  private void change(
      final String kind,
      final ApiType type,
      final ApiMember member,
      final ApiMember current,
      final ApiLocation location) {
    changes.add(
        new ApiChange(
            kind, type.name(), member.name(), member.signature(), current.signature(), location));
  }
}
