package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import org.junit.jupiter.api.Test;

class JavaApiSurfaceDetailsTest {
  private static final int MEMBER_LINE = 3;

  @Test
  void aClassWithOnlyPrivateConstructorsIsNotExtendable() {
    final ApiType api =
        ApiSurfaceFixtures.type(
                ApiSurfaceFixtures.read(
                    ApiSurfaceFixtures.PACKAGE
                        + "public class S { private S() {} public static S of() { return null; } }"),
                "p.S")
            .orElseThrow();

    assertThat(api.extendable()).isFalse();
    assertThat(ApiSurfaceFixtures.ids(api)).containsExactly("of()");
  }

  @Test
  void typeVariablesAreComparedByPositionAndErasedToTheirBound() {
    final ApiType first =
        ApiSurfaceFixtures.type(
                ApiSurfaceFixtures.read(
                    ApiSurfaceFixtures.PACKAGE
                        + "public class Box<T extends Number> { public T get(T in) { return in; } }"),
                "p.Box")
            .orElseThrow();
    final ApiType renamed =
        ApiSurfaceFixtures.type(
                ApiSurfaceFixtures.read(
                    ApiSurfaceFixtures.PACKAGE
                        + "public class Box<E extends Number> { public E get(E in) { return in; } }"),
                "p.Box")
            .orElseThrow();

    final ApiMember get = ApiSurfaceFixtures.member(first, "get(java.lang.Number)");
    assertThat(get.type().text()).isEqualTo("T");
    assertThat(get.type().key())
        .isEqualTo(ApiSurfaceFixtures.member(renamed, "get(java.lang.Number)").type().key());
    assertThat(first.typeParameters().getFirst().bounds())
        .extracting(ApiTypeRef::text)
        .containsExactly("java.lang.Number");
  }

  @Test
  void genericArgumentsVarargsAndSupertypesAreRead() {
    final ApiType api =
        ApiSurfaceFixtures.type(
                ApiSurfaceFixtures.read(
                    ApiSurfaceFixtures.PACKAGE
                        + "import java.util.*;\n"
                        + "public class A extends AbstractList<String> implements java.io.Closeable {"
                        + " public void log(String... parts) {} public void accept(List<? extends Object> items) {}"
                        + " public void close() {} public String get(int i) { return null; }"
                        + " public int size() { return 0; } }\n"),
                "p.A")
            .orElseThrow();

    assertThat(api.supertypes())
        .extracting(ApiTypeRef::text)
        .containsExactly("java.util.AbstractList<java.lang.String>", "java.io.Closeable");
    assertThat(ApiSurfaceFixtures.member(api, "log(java.lang.String[])").isVarargs()).isTrue();
    assertThat(
            ApiSurfaceFixtures.member(api, "accept(java.util.List)")
                .parameters()
                .getFirst()
                .type()
                .text())
        .isEqualTo("java.util.List<? extends java.lang.Object>");
  }

  @Test
  void locationIsTheDeclaration() {
    final ApiType api =
        ApiSurfaceFixtures.type(
                ApiSurfaceFixtures.read(
                    ApiSurfaceFixtures.PACKAGE + "public class A {\n  public void m() {}\n}\n"),
                "p.A")
            .orElseThrow();

    assertThat(api.location().line()).isEqualTo(2);
    assertThat(ApiSurfaceFixtures.member(api, "m()").location().line()).isEqualTo(MEMBER_LINE);
  }

  @Test
  void syntaxErrorNamesTheFile() {
    assertThatThrownBy(
            () ->
                ApiSurfaceFixtures.read(ApiSurfaceFixtures.PACKAGE + "public class A { void m( }"))
        .isInstanceOf(ApiSurfaceException.class)
        .extracting(error -> ((ApiSurfaceException) error).path())
        .isEqualTo("p/F0.java");
  }
}
