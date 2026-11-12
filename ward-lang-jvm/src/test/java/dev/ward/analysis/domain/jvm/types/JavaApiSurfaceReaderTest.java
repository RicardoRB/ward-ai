package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JavaApiSurfaceReaderTest {
  private static final String PACKAGE = "package p;\n";

  private static List<ApiType> read(final String... sources) {
    final List<SourceUnit> units = new java.util.ArrayList<>();
    for (int index = 0; index < sources.length; index++) {
      units.add(new SourceUnit("p/F" + index + ".java", AnalysisLanguage.JAVA, sources[index]));
    }
    return new JavaApiSurfaceReader()
        .read(
            units,
            new AntlrLanguageFrontend(),
            name -> ClassLoader.getSystemResource(name.replace('.', '/') + ".class") != null);
  }

  private static Optional<ApiType> type(final List<ApiType> types, final String name) {
    return types.stream().filter(found -> found.name().equals(name)).findFirst();
  }

  private static List<String> ids(final ApiType type) {
    return type.members().stream().filter(type::exposes).map(ApiMember::id).toList();
  }

  @Test
  void packagePrivateTypesAndMembersAreNotApi() {
    final List<ApiType> types =
        read(
            PACKAGE
                + "public class A { void hidden() {} public void shown() {} private int x; }\n"
                + "class B { public void m() {} }\n");

    assertThat(types.stream().filter(ApiType::isVisible))
        .extracting(ApiType::name)
        .containsExactly("p.A");
    assertThat(type(types, "p.B").orElseThrow().visibility()).isEqualTo(ApiVisibility.PACKAGE);
    assertThat(ids(types.getFirst())).containsExactlyInAnyOrder("shown()", "<init>()");
  }

  @Test
  void protectedMembersCountOnlyInExtendableTypes() {
    final List<ApiType> types =
        read(
            PACKAGE
                + "public class Open { protected void m() {} }\n"
                + "public final class Closed { protected void m() {} }\n");

    assertThat(ids(type(types, "p.Open").orElseThrow())).contains("m()");
    assertThat(ids(type(types, "p.Closed").orElseThrow())).doesNotContain("m()");
  }

  @Test
  void interfaceMembersArePublicAndDefaultIsRecognized() {
    final ApiType api =
        type(
                read(
                    PACKAGE
                        + "public interface I { void close(); default void flush() {}"
                        + " static void of() {} int LIMIT = 3; class Nested {} }\n"),
                "p.I")
            .orElseThrow();

    final ApiMember close = member(api, "close()");
    assertThat(close.visibility()).isEqualTo(ApiVisibility.PUBLIC);
    assertThat(close.isAbstract()).isTrue();
    assertThat(member(api, "flush()").isDefault()).isTrue();
    assertThat(member(api, "of()").isStatic()).isTrue();
    assertThat(member(api, "LIMIT").isFinal()).isTrue();
    assertThat(api.extendable()).isTrue();
  }

  @Test
  void nestedTypeOfAnInterfaceIsPublicAndOthersNeedAnApiParent() {
    final List<ApiType> types =
        read(
            PACKAGE
                + "public interface I { class Nested {} }\n"
                + "public class C { public static class In {} private static class Hidden {}"
                + " protected static class Sub {} static class Pkg {} }\n"
                + "class P { public static class Out {} }\n");

    assertThat(types.stream().filter(ApiType::isVisible))
        .extracting(ApiType::name)
        .containsExactly("p.C", "p.C.In", "p.C.Sub", "p.I", "p.I.Nested", "p.P.Out");
    assertThat(type(types, "p.P").orElseThrow().isVisible()).isFalse();
  }

  @Test
  void implicitConstructorFollowsTheClassAndEnumConstructorsAreHidden() {
    final List<ApiType> types =
        read(
            PACKAGE
                + "public class A {}\n"
                + "public enum E { ONE, TWO; E() {} }\n"
                + "public record R(int x, String y) {}\n");

    assertThat(ids(type(types, "p.A").orElseThrow())).containsExactly("<init>()");
    assertThat(ids(type(types, "p.E").orElseThrow()))
        .containsExactlyInAnyOrder("ONE", "TWO", "values()", "valueOf(java.lang.String)");
    assertThat(member(type(types, "p.E").orElseThrow(), "<init>()").visibility())
        .isEqualTo(ApiVisibility.PRIVATE);
    assertThat(ids(type(types, "p.R").orElseThrow()))
        .contains("<init>(int,java.lang.String)", "x()", "y()");
    assertThat(type(types, "p.E").orElseThrow().isFinal()).isTrue();
  }

  @Test
  void aClassWithOnlyPrivateConstructorsIsNotExtendable() {
    final ApiType api =
        type(
                read(
                    PACKAGE
                        + "public class S { private S() {} public static S of() { return null; } }"),
                "p.S")
            .orElseThrow();

    assertThat(api.extendable()).isFalse();
    assertThat(ids(api)).containsExactly("of()");
  }

  @Test
  void typeVariablesAreComparedByPositionAndErasedToTheirBound() {
    final ApiType first =
        type(
                read(
                    PACKAGE
                        + "public class Box<T extends Number> { public T get(T in) { return in; } }"),
                "p.Box")
            .orElseThrow();
    final ApiType renamed =
        type(
                read(
                    PACKAGE
                        + "public class Box<E extends Number> { public E get(E in) { return in; } }"),
                "p.Box")
            .orElseThrow();

    final ApiMember get = member(first, "get(java.lang.Number)");
    assertThat(get.type().text()).isEqualTo("T");
    assertThat(get.type().key()).isEqualTo(member(renamed, "get(java.lang.Number)").type().key());
    assertThat(first.typeParameters().getFirst().bounds())
        .extracting(b -> b.text())
        .containsExactly("java.lang.Number");
  }

  @Test
  void genericArgumentsVarargsAndSupertypesAreRead() {
    final ApiType api =
        type(
                read(
                    PACKAGE
                        + "import java.util.*;\n"
                        + "public class A extends AbstractList<String> implements java.io.Closeable {"
                        + " public void log(String... parts) {} public void accept(List<? extends Object> items) {}"
                        + " public void close() {} public String get(int i) { return null; }"
                        + " public int size() { return 0; } }\n"),
                "p.A")
            .orElseThrow();

    assertThat(api.supertypes())
        .extracting(t -> t.text())
        .containsExactly("java.util.AbstractList<java.lang.String>", "java.io.Closeable");
    assertThat(member(api, "log(java.lang.String[])").isVarargs()).isTrue();
    assertThat(member(api, "accept(java.util.List)").parameters().getFirst().type().text())
        .isEqualTo("java.util.List<? extends java.lang.Object>");
  }

  @Test
  void locationIsTheDeclaration() {
    final ApiType api =
        type(read(PACKAGE + "public class A {\n  public void m() {}\n}\n"), "p.A").orElseThrow();

    assertThat(api.location().line()).isEqualTo(2);
    assertThat(member(api, "m()").location().line()).isEqualTo(3);
  }

  @Test
  void syntaxErrorNamesTheFile() {
    assertThatThrownBy(() -> read(PACKAGE + "public class A { void m( }"))
        .isInstanceOf(ApiSurfaceException.class)
        .extracting(error -> ((ApiSurfaceException) error).path())
        .isEqualTo("p/F0.java");
  }

  private static ApiMember member(final ApiType type, final String id) {
    return type.members().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
  }
}
