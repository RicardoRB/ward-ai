package dev.ward.domain.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiLocation;
import org.junit.jupiter.api.Test;

class ApiChangeTest {
  private static final ApiLocation AT = new ApiLocation("A.java", 3, 1);

  @Test
  void memberMessageNamesTypeMemberAndBothSignatures() {
    final ApiChange change =
        new ApiChange(
            "return-type-changed",
            "com.acme.User",
            "name",
            "public java.lang.String name()",
            "public int name()",
            AT);

    assertThat(change.message())
        .isEqualTo("com.acme.User#name: public java.lang.String name() -> public int name()");
  }

  @Test
  void typeMessageOmitsTheMemberAndRemovedEndsTheMessage() {
    final ApiChange change =
        new ApiChange(
            "type-removed",
            "com.acme.Client",
            "",
            "public class com.acme.Client",
            ApiChange.REMOVED,
            AT);

    assertThat(change.message())
        .isEqualTo("com.acme.Client: public class com.acme.Client -> removed");
    assertThat(change.message()).endsWith("removed");
  }
}
