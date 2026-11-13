package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Decides which kind of incompatible change a member that exists in both versions has, if any. */
final class ApiMemberKinds {
  private ApiMemberKinds() {}

  /**
   * Returns the kind of change from {@code member} of {@code old} to {@code current}, or null when
   * the change is compatible. The first matching kind wins.
   */
  static String of(final ApiType old, final ApiMember member, final ApiMember current) {
    if (current.visibility().lessVisibleThan(member.visibility())) {
      return "member-less-visible";
    }
    if (current.isStatic() != member.isStatic()) {
      return "static-changed";
    }
    final String shape = shape(member, current);
    if (shape != null) {
      return shape;
    }
    final String inherited = inheritance(old, member, current);
    return inherited == null ? sourceKind(member, current) : inherited;
  }

  /** The changes of a language with null safety and default arguments, which Java never has. */
  private static String sourceKind(final ApiMember member, final ApiMember current) {
    if (member.kind() == ApiMember.Kind.FIELD) {
      return null;
    }
    if (nullabilityNarrowed(member, current)) {
      return "nullability-narrowed";
    }
    return defaultRemoved(member, current) ? "default-argument-removed" : null;
  }

  /** A return type that may now be null, or a parameter that no longer accepts null. */
  private static boolean nullabilityNarrowed(final ApiMember member, final ApiMember current) {
    final boolean returns =
        member.kind() == ApiMember.Kind.METHOD
            && !member.type().nullable()
            && current.type().nullable();
    return returns
        || IntStream.range(0, Math.min(member.parameters().size(), current.parameters().size()))
            .anyMatch(
                index ->
                    member.parameters().get(index).type().nullable()
                        && !current.parameters().get(index).type().nullable());
  }

  private static boolean defaultRemoved(final ApiMember member, final ApiMember current) {
    return IntStream.range(0, Math.min(member.parameters().size(), current.parameters().size()))
        .anyMatch(
            index ->
                member.parameters().get(index).hasDefault()
                    && !current.parameters().get(index).hasDefault());
  }

  private static String shape(final ApiMember member, final ApiMember current) {
    final boolean method = member.kind() != ApiMember.Kind.FIELD;
    if (!member.type().isInferred()
        && !current.type().isInferred()
        && !member.type().key().equals(current.type().key())) {
      return method ? "return-type-changed" : "field-type-changed";
    }
    return method && signatureChanged(member, current) ? "signature-changed" : null;
  }

  private static boolean signatureChanged(final ApiMember member, final ApiMember current) {
    return !ApiSignatures.parameterKeys(member.parameters())
            .equals(ApiSignatures.parameterKeys(current.parameters()))
        || member.isVarargs() != current.isVarargs()
        || !bounds(member.typeParameters()).equals(bounds(current.typeParameters()));
  }

  private static List<String> bounds(final List<ApiTypeParameter> parameters) {
    return parameters.stream()
        .map(
            parameter ->
                parameter.bounds().stream().map(ApiTypeRef::key).collect(Collectors.joining("&")))
        .toList();
  }

  /** The changes that break callers or subclasses: a final field, an abstract or final method. */
  private static String inheritance(
      final ApiType old, final ApiMember member, final ApiMember current) {
    if (member.kind() == ApiMember.Kind.FIELD) {
      return !member.isFinal() && current.isFinal() ? "field-now-final" : null;
    }
    return member.kind() == ApiMember.Kind.METHOD && old.extendable()
        ? methodInheritance(member, current)
        : null;
  }

  private static String methodInheritance(final ApiMember member, final ApiMember current) {
    if (!member.isAbstract() && current.isAbstract()) {
      return "method-now-abstract";
    }
    return !member.isFinal() && current.isFinal() ? "method-now-final" : null;
  }
}
