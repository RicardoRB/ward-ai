package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KotlinApiMemberReaderTest {
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
  void propertiesAreAccessorsAndVarHasASetter() {
    final ApiType api =
        type(
            read(
                PACKAGE
                    + "open class A(val id: Int, var name: String?) {\n"
                    + "  val ok: Boolean = true\n  val isOn: Boolean = true\n"
                    + "  private var secret: Int = 0\n}\n"),
            "p.A");

    assertThat(ApiSurfaceFixtures.ids(api))
        .containsExactlyInAnyOrder(
            "<init>(int,java.lang.String)",
            "getId()",
            "getName()",
            "setName(java.lang.String)",
            "getOk()",
            "isOn()");
    assertThat(ApiSurfaceFixtures.member(api, "getName()").type().nullable()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "getId()").isFinal()).isTrue();
  }

  @Test
  void defaultArgumentsNullabilityVarargAndExtensionsAreRead() {
    final ApiType api =
        type(
            read(
                PACKAGE
                    + "class A {\n"
                    + "  fun f(a: Int, b: String = \"x\", c: String?): Int? = null\n"
                    + "  fun g(vararg xs: Int) {}\n"
                    + "  fun String.ext(n: Int) {}\n}\n"),
            "p.A");

    final ApiMember f = ApiSurfaceFixtures.member(api, "f(int,java.lang.String,java.lang.String)");
    assertThat(f.parameters().get(1).hasDefault()).isTrue();
    assertThat(f.parameters().get(2).type().nullable()).isTrue();
    assertThat(f.type().isInferred()).isFalse();
    assertThat(ApiSurfaceFixtures.member(api, "g(int[])").isVarargs()).isTrue();
    assertThat(ApiSurfaceFixtures.ids(api)).contains("ext(java.lang.String,int)");
  }

  @Test
  void aReturnTypeLeftToInferenceIsUnknown() {
    final ApiType api =
        type(read(PACKAGE + "class A {\n fun f() = 1\n fun g() {}\n fun h(): Int = 1\n}\n"), "p.A");

    assertThat(ApiSurfaceFixtures.member(api, "f()").type().isInferred()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "g()").type().text()).isEqualTo("void");
    assertThat(ApiSurfaceFixtures.member(api, "h()").type().text()).isEqualTo("int");
  }

  @Test
  void topLevelDeclarationsOfAPackageShareOneType() {
    final List<ApiType> types =
        read(
            PACKAGE + "fun one() {}\nval two: Int = 2\nprivate fun hidden() {}\n",
            PACKAGE + "fun three(x: Int) {}\n");

    final ApiType facade = type(types, "p.<top-level>");
    assertThat(ApiSurfaceFixtures.ids(facade))
        .containsExactlyInAnyOrder("one()", "getTwo()", "three(int)");
    assertThat(ApiSurfaceFixtures.member(facade, "hidden()").visibility())
        .isEqualTo(ApiVisibility.PRIVATE);
  }

  @Test
  void interfaceMembersAreAbstractUnlessTheyHaveABody() {
    final ApiType api =
        type(read(PACKAGE + "interface I {\n fun a()\n fun b() {}\n val c: Int\n}\n"), "p.I");

    assertThat(ApiSurfaceFixtures.member(api, "a()").isAbstract()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "b()").isDefault()).isTrue();
    assertThat(ApiSurfaceFixtures.member(api, "getC()").isAbstract()).isTrue();
  }
}
