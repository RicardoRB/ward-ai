package dev.ward.analysis.domain.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiSourceSignatureTest {
  private static final ApiLocation AT = new ApiLocation("A.java", 1, 1);
  private static final ApiTypeRef STRING = ApiTypeRef.of("java.lang.String");
  private static final ApiTypeRef INT = ApiTypeRef.of("int");

  @Test
  void signaturesShowTypeParametersBoundsAndDefaults() {
    final ApiMember generic =
        new ApiMember(
            ApiMember.Kind.METHOD,
            "put",
            ApiVisibility.PUBLIC,
            false,
            false,
            false,
            false,
            false,
            List.of(
                new ApiTypeParameter("T", List.of(STRING, INT), ""),
                new ApiTypeParameter("U", List.of(), "")),
            List.of(new ApiParameter(STRING, true)),
            INT,
            AT);

    assertThat(generic.signature())
        .isEqualTo("public <T extends java.lang.String & int, U> int put(java.lang.String = ...)");
  }

  @Test
  void aTypeWrittenNowhereIsInferred() {
    final ApiTypeRef inferred =
        new ApiTypeRef(ApiTypeRef.INFERRED, ApiTypeRef.INFERRED, ApiTypeRef.INFERRED, false);

    assertThat(inferred.isInferred()).isTrue();
    assertThat(STRING.isInferred()).isFalse();
  }
}
