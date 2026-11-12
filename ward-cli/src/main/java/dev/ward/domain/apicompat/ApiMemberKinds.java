package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import java.util.List;
import java.util.stream.Collectors;

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
    if (current.isStatic() == member.isStatic()) {
      final String shape = shape(member, current);
      return shape == null ? inheritance(old, member, current) : shape;
    }
    return "static-changed";
  }

  private static String shape(final ApiMember member, final ApiMember current) {
    final boolean method = member.kind() != ApiMember.Kind.FIELD;
    if (!member.type().key().equals(current.type().key())) {
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
