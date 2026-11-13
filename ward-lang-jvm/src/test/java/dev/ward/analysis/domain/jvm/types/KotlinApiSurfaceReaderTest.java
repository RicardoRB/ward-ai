package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KotlinApiSurfaceReaderTest {
  private static final String PACKAGE = "package p\n";

  private static List<ApiType> read(final String... sources) {
    final List<SourceUnit> units = new ArrayList<>();
    for (int index = 0; index < sources.length; index++) {
      units.add(new SourceUnit("p/F" + index + ".kt", AnalysisLanguage.KOTLIN, sources[index]));
    }
    return new KotlinApiSurfaceReader()
        .read(
            units,
            new AntlrLanguageFrontend(),
            name -> ClassLoader.getSystemResource(name.replace('.', '/') + ".class") != null);
  }

  private static ApiType type(final List<ApiType> types, final String name) {
    return ApiSurfaceFixtures.type(types, name).orElseThrow();
  }

  @Test
  void visibilityDefaultsToPublicAndInternalIsNotApi() {
    final List<ApiType> types =
        read(
            PACKAGE
                + "open class A {\n fun shown() {}\n internal fun hidden() {}\n"
                + " private fun secret() {}\n protected fun guarded() {}\n}\n"
                + "internal class B {\n fun m() {}\n}\n"
                + "private class C\n");

    assertThat(type(types, "p.A").visibility()).isEqualTo(ApiVisibility.PUBLIC);
    assertThat(type(types, "p.B").isVisible()).isFalse();
    assertThat(type(types, "p.C").isVisible()).isFalse();
    assertThat(ApiSurfaceFixtures.ids(type(types, "p.A")))
        .containsExactlyInAnyOrder("shown()", "<init>()", "guarded()");
    assertThat(ApiSurfaceFixtures.member(type(types, "p.A"), "hidden()").visibility())
        .isEqualTo(ApiVisibility.PACKAGE);
  }

  @Test
  void classesAreClosedUnlessOpenAbstractOrSealed() {
    final List<ApiType> types =
        read(
            PACKAGE
                + "class Closed\nopen class Open\nabstract class Abstract\nsealed class Sealed\n"
                + "interface Face\nobject Single\nenum class Color { RED, GREEN }\n"
                + "data class Pair2(val a: Int, val b: String?)\n");

    assertThat(type(types, "p.Closed").isFinal()).isTrue();
    assertThat(type(types, "p.Open").extendable()).isTrue();
    assertThat(type(types, "p.Abstract").extendable()).isTrue();
    assertThat(type(types, "p.Abstract").isAbstract()).isTrue();
    assertThat(type(types, "p.Sealed").isSealed()).isTrue();
    assertThat(type(types, "p.Sealed").extendable()).isFalse();
    assertThat(type(types, "p.Face").kind()).isEqualTo(ApiType.Kind.INTERFACE);
    assertThat(type(types, "p.Single").kind()).isEqualTo(ApiType.Kind.OBJECT);
    assertThat(type(types, "p.Color").kind()).isEqualTo(ApiType.Kind.ENUM);
    assertThat(ApiSurfaceFixtures.ids(type(types, "p.Color")))
        .containsExactlyInAnyOrder("RED", "GREEN");
    assertThat(ApiSurfaceFixtures.ids(type(types, "p.Pair2")))
        .contains("component1()", "component2()", "copy(int,java.lang.String)", "getA()", "getB()");
  }

  @Test
  void typeParametersKeepBoundsAndVariance() {
    final ApiType api =
        type(read(PACKAGE + "interface Box<out T : Comparable<T>, in U> where U : Any\n"), "p.Box");

    assertThat(api.typeParameters()).hasSize(2);
    assertThat(api.typeParameters().get(0).variance()).isEqualTo("out");
    assertThat(api.typeParameters().get(0).bounds()).hasSize(1);
    assertThat(api.typeParameters().get(1).variance()).isEqualTo("in");
    assertThat(api.typeParameters().get(1).bounds()).hasSize(1);
  }

  @Test
  void nestedTypesAndCompanionsAreNamedByTheirOwner() {
    final List<ApiType> types =
        read(PACKAGE + "class Outer {\n class Inner\n companion object {\n fun make() {}\n }\n}\n");

    assertThat(types)
        .extracting(ApiType::name)
        .containsExactly("p.Outer", "p.Outer.Companion", "p.Outer.Inner");
  }
}
