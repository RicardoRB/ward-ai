package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Compares the API of a working tree with the API at a baseline and lists the incompatible changes.
 * It is pure and language-neutral: it sees only {@link ApiType}s.
 */
public final class ApiComparison {
  private static final String EMPTY = "";

  private final ApiIndex before;
  private final ApiIndex after;
  private final List<ApiChange> changes = new ArrayList<>();

  private ApiComparison(final List<ApiType> baseline, final List<ApiType> current) {
    this.before = new ApiIndex(baseline);
    this.after = new ApiIndex(current);
  }

  /**
   * Returns the incompatible changes from {@code baseline} to {@code current}, ordered by file,
   * line and kind.
   */
  public static List<ApiChange> compare(final List<ApiType> baseline, final List<ApiType> current) {
    return compare(baseline, current, name -> true);
  }

  /**
   * Returns the incompatible changes of the baseline types whose qualified name {@code inScope}
   * accepts; the other types still count as supertypes.
   */
  public static List<ApiChange> compare(
      final List<ApiType> baseline, final List<ApiType> current, final Predicate<String> inScope) {
    final ApiComparison comparison = new ApiComparison(baseline, current);
    baseline.stream()
        .filter(type -> inScope.test(type.name()) && comparison.before.isApi(type.name()))
        .forEach(comparison::compareType);
    return comparison.changes.stream()
        .sorted(
            Comparator.comparing((ApiChange change) -> change.location().path())
                .thenComparingInt(change -> change.location().line())
                .thenComparingInt(change -> change.location().column())
                .thenComparing(ApiChange::kind)
                .thenComparing(ApiChange::typeName)
                .thenComparing(ApiChange::member)
                .thenComparing(ApiChange::before))
        .toList();
  }

  private void compareType(final ApiType old) {
    final Optional<ApiType> found = after.find(old.name());
    if (found.isEmpty()) {
      changes.add(
          new ApiChange(
              "type-removed",
              old.name(),
              EMPTY,
              ApiSignatures.of(old),
              ApiChange.REMOVED,
              old.location()));
      return;
    }
    final ApiType now = found.get();
    if (!after.isApi(now.name()) || now.visibility().lessVisibleThan(old.visibility())) {
      typeChange("type-less-visible", old, now);
    } else if (now.kind() != old.kind()) {
      typeChange("type-kind-changed", old, now);
    } else {
      compareShape(old, now);
      new ApiMemberComparison(before, after, changes).compare(old, now);
    }
  }

  private void compareShape(final ApiType old, final ApiType now) {
    if (old.extendable() && (now.isFinal() || now.isSealed())) {
      typeChange("type-now-final", old, now);
    }
    if (old.kind() == ApiType.Kind.CLASS && instantiable(old) && now.isAbstract()) {
      typeChange("type-now-abstract", old, now);
    }
    if (ApiSignatures.typeParametersDiffer(old.typeParameters(), now.typeParameters())) {
      typeChange("type-parameters-changed", old, now);
    }
    final Set<String> remaining = after.supertypeNames(now);
    final Set<String> removed = new HashSet<>(before.supertypeNames(old));
    removed.removeAll(remaining);
    old.supertypes().stream()
        .map(supertype -> supertype.erasure())
        .filter(removed::contains)
        .forEach(name -> removedSupertype(old, now, name));
    before.supertypeNames(old).stream()
        .filter(removed::contains)
        .filter(name -> old.supertypes().stream().noneMatch(s -> s.erasure().equals(name)))
        .forEach(name -> removedSupertype(old, now, name));
  }

  private static boolean instantiable(final ApiType type) {
    return !type.isAbstract()
        && type.members().stream()
            .anyMatch(
                member ->
                    member.kind() == ApiMember.Kind.CONSTRUCTOR
                        && member.visibility()
                            == dev.ward.analysis.domain.api.ApiVisibility.PUBLIC);
  }

  private void typeChange(final String kind, final ApiType old, final ApiType now) {
    changes.add(
        new ApiChange(
            kind, old.name(), EMPTY, ApiSignatures.of(old), ApiSignatures.of(now), now.location()));
  }

  private void removedSupertype(final ApiType old, final ApiType now, final String name) {
    changes.add(
        new ApiChange(
            "supertype-removed",
            old.name(),
            EMPTY,
            ApiSignatures.of(old) + " : " + name,
            ApiSignatures.of(now),
            now.location()));
  }
}
