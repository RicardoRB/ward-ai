package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaApiSurfaceReaderTest {
  @Test
  void packagePrivateTypesAndMembersAreNotApi() {
    final List<ApiType> types =
        ApiSurfaceFixtures.read(
            ApiSurfaceFixtures.PACKAGE
                + "public class A { void hidden() {} public void shown() {} private int x; }\n"
                + "class B { public void m() {} }\n");

    assertThat(types.stream().filter(ApiType::isVisible))
        .extracting(ApiType::name)
        .containsExactly("p.A");
    assertThat(ApiSurfaceFixtures.type(types, "p.B").orElseThrow().visibility())
        .isEqualTo(ApiVisibility.PACKAGE);
    assertThat(ApiSurfaceFixtures.ids(types.getFirst()))
        .containsExactlyInAnyOrder("shown()", "<init>()");
  }

  @Test
  void protectedMembersCountOnlyInExtendableTypes() {
    final List<ApiType> types =
        ApiSurfaceFixtures.read(
            ApiSurfaceFixtures.PACKAGE
                + "public class Open { protected void m() {} }\n"
                + "public final class Closed { protected void m() {} }\n");

    assertThat(ApiSurfaceFixtures.ids(ApiSurfaceFixtures.type(types, "p.Open").orElseThrow()))
        .contains("m()");
    assertThat(ApiSurfaceFixtures.ids(ApiSurfaceFixtures.type(types, "p.Closed").orElseThrow()))
        .doesNotContain("m()");
  }

  @Test
  void interfaceMembersArePublicAndDefaultIsRecognized() {
    final ApiType api =
        ApiSurfaceFixtures.type(
                ApiSurfaceFixtures.read(
                    ApiSurfaceFixtures.PACKAGE
                        + "public interface I { void close(); default void flush() {}"
                        + " static void of() {} int LIMIT = 3; class Nested {} }\n"),
                "p.I")
            .orElseThrow();

    final ApiMember close = ApiSurfaceFixtures.member(api, "close()");
    assertThat(close.visibility()).isEqualTo(ApiVisibility.PUBLIC);
    assertThat(close.isAbstract()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "flush()").isDefault()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "of()").isStatic()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "LIMIT").isFinal()).isTrue();
    assertThat(api.extendable()).isTrue();
  }

  @Test
  void nestedTypeOfAnInterfaceIsPublicAndOthersNeedAnApiParent() {
    final List<ApiType> types =
        ApiSurfaceFixtures.read(
            ApiSurfaceFixtures.PACKAGE
                + "public interface I { class Nested {} }\n"
                + "public class C { public static class In {} private static class Hidden {}"
                + " protected static class Sub {} static class Pkg {} }\n"
                + "class P { public static class Out {} }\n");

    assertThat(types.stream().filter(ApiType::isVisible))
        .extracting(ApiType::name)
        .containsExactly("p.C", "p.C.In", "p.C.Sub", "p.I", "p.I.Nested", "p.P.Out");
    assertThat(ApiSurfaceFixtures.type(types, "p.P").orElseThrow().isVisible()).isFalse();
  }

  @Test
  void implicitConstructorFollowsTheClassAndEnumConstructorsAreHidden() {
    final List<ApiType> types =
        ApiSurfaceFixtures.read(
            ApiSurfaceFixtures.PACKAGE
                + "public class A {}\n"
                + "public enum E { ONE, TWO; E() {} }\n"
                + "public record R(int x, String y) {}\n");

    assertThat(ApiSurfaceFixtures.ids(ApiSurfaceFixtures.type(types, "p.A").orElseThrow()))
        .containsExactly("<init>()");
    assertThat(ApiSurfaceFixtures.ids(ApiSurfaceFixtures.type(types, "p.E").orElseThrow()))
        .containsExactlyInAnyOrder("ONE", "TWO", "values()", "valueOf(java.lang.String)");
    assertThat(
            ApiSurfaceFixtures.member(
                    ApiSurfaceFixtures.type(types, "p.E").orElseThrow(), "<init>()")
                .visibility())
        .isEqualTo(ApiVisibility.PRIVATE);
    assertThat(ApiSurfaceFixtures.ids(ApiSurfaceFixtures.type(types, "p.R").orElseThrow()))
        .contains("<init>(int,java.lang.String)", "x()", "y()");
    assertThat(ApiSurfaceFixtures.type(types, "p.E").orElseThrow().isFinal()).isTrue();
  }
}
