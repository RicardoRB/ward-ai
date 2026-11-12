package dev.ward.analysis.domain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiModelTest {
  private static final ApiLocation AT = new ApiLocation("A.java", 1, 1);
  private static final ApiTypeRef STRING = ApiTypeRef.of("java.lang.String");
  private static final ApiTypeRef INT = ApiTypeRef.of("int");

  private static ApiMember method(
      final String name, final ApiTypeRef returns, final ApiTypeRef... in) {
    return member(ApiMember.Kind.METHOD, name, returns, in);
  }

  private static ApiMember member(
      final ApiMember.Kind kind,
      final String name,
      final ApiTypeRef type,
      final ApiTypeRef... parameters) {
    return new ApiMember(
        kind,
        name,
        ApiVisibility.PUBLIC,
        false,
        false,
        false,
        false,
        false,
        List.of(),
        java.util.Arrays.stream(parameters).map(found -> new ApiParameter(found, false)).toList(),
        type,
        AT);
  }

  private static ApiType type(
      final ApiType.Kind kind,
      final boolean isFinal,
      final boolean sealed,
      final ApiMember... members) {
    return new ApiType(
        "p.A",
        kind,
        ApiVisibility.PUBLIC,
        isFinal,
        sealed,
        false,
        List.of(),
        List.of(),
        List.of(members),
        AT);
  }

  @Test
  void methodIdentityIsNameAndErasedParameters() {
    assertThat(method("put", INT, STRING, INT).id()).isEqualTo("put(java.lang.String,int)");
    assertThat(member(ApiMember.Kind.FIELD, "size", INT).id()).isEqualTo("size");
  }

  @Test
  void signaturesAreWrittenForMessages() {
    assertThat(method("name", STRING).signature()).isEqualTo("public java.lang.String name()");
    assertThat(member(ApiMember.Kind.CONSTRUCTOR, "<init>", ApiTypeRef.of("void"), INT).signature())
        .isEqualTo("public <init>(int)");
    assertThat(member(ApiMember.Kind.FIELD, "size", INT).signature()).isEqualTo("public int size");
  }

  @Test
  void visibilityOrdersFromPublicToPrivate() {
    assertThat(ApiVisibility.PROTECTED.lessVisibleThan(ApiVisibility.PUBLIC)).isTrue();
    assertThat(ApiVisibility.PACKAGE.lessVisibleThan(ApiVisibility.PROTECTED)).isTrue();
    assertThat(ApiVisibility.PUBLIC.lessVisibleThan(ApiVisibility.PUBLIC)).isFalse();
  }

  @Test
  void extendableTypes() {
    final ApiMember constructor =
        member(ApiMember.Kind.CONSTRUCTOR, "<init>", ApiTypeRef.of("void"));

    assertThat(type(ApiType.Kind.INTERFACE, false, false).extendable()).isTrue();
    assertThat(type(ApiType.Kind.CLASS, false, false, constructor).extendable()).isTrue();
    assertThat(type(ApiType.Kind.CLASS, false, false).extendable()).isFalse();
    assertThat(type(ApiType.Kind.CLASS, true, false, constructor).extendable()).isFalse();
    assertThat(type(ApiType.Kind.INTERFACE, false, true).extendable()).isFalse();
    assertThat(type(ApiType.Kind.ENUM, false, false, constructor).extendable()).isFalse();
  }

  @Test
  void recordsCopyTheirLists() {
    final List<ApiMember> members = new ArrayList<>(List.of(method("a", INT)));
    final ApiType api =
        new ApiType(
            "p.A",
            ApiType.Kind.CLASS,
            ApiVisibility.PUBLIC,
            false,
            false,
            false,
            List.of(),
            List.of(),
            members,
            AT);
    members.clear();

    assertThat(api.members()).hasSize(1);
  }

  @Test
  void locationIsOneBased() {
    assertThatThrownBy(() -> new ApiLocation("A.java", 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
